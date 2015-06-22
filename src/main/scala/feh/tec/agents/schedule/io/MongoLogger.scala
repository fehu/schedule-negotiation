package feh.tec.agents.schedule.io

import akka.actor.{ActorLogging, Props, ActorRefFactory, ActorRef}
import feh.tec.agents.comm._
import feh.tec.agents.schedule.{ProfessorAgent, CoordinatorAgent, GroupAgent, StudentAgent}
import reactivemongo.api._
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson.{BSONDocument, BSONDocumentWriter}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

protected[io] class MongoLogger(val id: SystemAgentId, collection: BSONCollection)
                               (implicit exContext: ExecutionContext,
                                         format: ReportLogFormat) extends ReportLogger
{
  implicit lazy val writer = ReportDistributedMongoLogger.reportDocumentWriter(format)

  def log(msg: Report): Unit = {
    collection.insert(msg)
  }

  protected def onMessageSent(msg: Message, to: AgentRef): Unit = {}
  def start() = {}
  def stop() = {}

}


class ReportDistributedMongoLogger(connection: MongoConnection)
                                  (implicit exContext: ExecutionContext,
                                            format: ReportLogFormat) extends ReportLogger with ActorLogging
{

  def log(msg: Report): Unit = loggers.collectFirst{
    case (filter, logger) if filter(msg.sender.id.role) => logger forward msg
  }.getOrElse(this.asInstanceOf[ActorLogging].log.debug(s"no logger found for $msg in $loggers"))

  val id = ReportDistributedMongoLogger.Id

  protected def onMessageSent(msg: Message, to: AgentRef): Unit = {}

  protected var loggers: List[(AgentRole => Boolean, ActorRef)] = Nil

  override def systemMessageReceived = ({
    case _: SystemMessage.Start => start()
  }: PartialFunction[SystemMessage, Unit]) orElse super.systemMessageReceived

  protected var dbConnection: DefaultDB = null

  def start(): Unit = {
    val db_ = connection("logs")
    db_.drop()
    implicit val db = connection("logs")

    dbConnection = db

    loggers = mkLogger(_ == GroupAgent.Role, "groups") ::
              mkLogger(_ == CoordinatorAgent.role, "controller") ::
              mkLogger(_ == StudentAgent.Role, "students") ::
              mkLogger(_.isInstanceOf[ProfessorAgent.Role], "professors") :: Nil
    this.asInstanceOf[ActorLogging].log.debug("loggers = " + loggers)
  }

  private def mkLogger(filter: AgentRole => Boolean, collectionName: String)(implicit db: DefaultDB) = {
    val collection = db.collection[BSONCollection](collectionName)
    val fut = collection.create().map{
      case true =>
        val id = SystemAgentId("logger-" + collectionName, ReportDistributedMongoLogger.LoggerRole)
        val logger = ReportDistributedMongoLogger.newLogger(id, collection)
        filter -> logger
      }
    Await.result(fut, 2000.millis)
  }


  def stop(): Unit = {
    dbConnection.connection.close()
  }
}

object ReportDistributedMongoLogger{
  val LoggerRole = SystemAgentRole("Logger")
  val Id = SystemAgentId("ReportDistributedMongoLogger", LoggerRole)

  protected [io] def newLogger(id: SystemAgentId, collection: BSONCollection)
                              (implicit afact: ActorRefFactory,
                                        format: ReportLogFormat) =
    afact.actorOf(Props(new MongoLogger(id, collection)(afact.dispatcher, implicitly)))

  def creator(connection: MongoConnection)
             (implicit exContext: ExecutionContext, format: ReportLogFormat) =
    AgentCreator(LoggerRole){_ => _ => new ReportDistributedMongoLogger(connection)}


  def reportDocumentWriter(format: ReportLogFormat): BSONDocumentWriter[Report] = new BSONDocumentWriter[Report]{
    def write(t: Report) = BSONDocument( "_id" -> t.uuid.toString
                                       , "sender" -> t.sender.id.toString
                                       , "report" -> format(t)
                                       )
  }
}