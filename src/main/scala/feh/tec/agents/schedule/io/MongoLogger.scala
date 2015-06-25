package feh.tec.agents.schedule.io

import akka.actor._
import feh.tec.agents.comm._
import feh.tec.agents.schedule.Messages.TimetableReport
import feh.tec.agents.schedule._
import reactivemongo.api._
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson._

import scala.collection.immutable.TreeMap
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

protected[io] class MongoLogger(val id: SystemAgentId, collection: BSONCollection)
                               (implicit exContext: ExecutionContext,
                                         format: ReportLogFormat,
                                         tDesr: TimeDescriptor[Time]) extends ReportLogger
{
  implicit lazy val writer = ReportDistributedMongoLogger.reportDocumentWriter(format, tDesr)

  def log(msg: Report): Unit = {
    collection.insert(msg).onFailure{ case thr: Throwable => throw thr }
  }

  protected def onMessageSent(msg: Message, to: AgentRef): Unit = {}
  def start() = {}
  def stop() = {}

  def receive_ : Receive = {
    case "stop" =>
      sender() ! "stopped"
      setStopped()
    case _ if stopped =>
  }

  override def receive = receive_ orElse super.receive
}


class ReportDistributedMongoLogger(connection: MongoConnection, timeout: Duration)
                                  (implicit exContext: ExecutionContext,
                                            format: ReportLogFormat,
                                            tDesr: TimeDescriptor[Time]) extends ReportLogger with ActorLogging
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

  protected lazy val dbConnection = connection("logs")

  def start(): Unit = {
    Await.ready(dbConnection.drop(), 5.seconds)

    implicit val db = dbConnection

    loggers = mkLogger(_ == GroupAgent.Role, "groups") ::
              mkLogger(_ == CoordinatorAgent.role, "controller") ::
              mkLogger(_ == StudentAgent.Role, "students") ::
              mkLogger(_.isInstanceOf[ProfessorAgent.Role], "professors") :: Nil
    this.asInstanceOf[ActorLogging].log.debug("loggers = " + loggers)
  }

  private def mkLogger(filter: AgentRole => Boolean, collectionName: String)
                      (implicit db: DefaultDB, tDesr: TimeDescriptor[Time]) =
  {
    val collection = db.collection[BSONCollection](collectionName)
    val fut = collection.create().map{
      case true =>
        val id = SystemAgentId("logger-" + collectionName, ReportDistributedMongoLogger.LoggerRole)
        val logger = ReportDistributedMongoLogger.newLogger(id, collection)
        filter -> logger
      }
    Await.result(fut, timeout)
  }


  def receive_ : Receive = {
    case "stopped" =>
      val r = sender()
      loggers = loggers.filter(_._2 != r)
      context.stop(r)
      this.asInstanceOf[ActorLogging].log.debug("a logger stopped")

      if(loggers.isEmpty){
        this.asInstanceOf[ActorLogging].log.debug("closing connection")
        dbConnection.connection.close()
        this.asInstanceOf[ActorLogging].log.debug("Stopped")
        context stop self
      }
  }
  override def receive = receive_ orElse super.receive

  def stop(): Unit = {
    loggers.foreach(_._2 ! "stop")
  }

  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy
}

object ReportDistributedMongoLogger{
  val LoggerRole = SystemAgentRole("Logger")
  val Id = SystemAgentId("ReportDistributedMongoLogger", LoggerRole)

  protected [io] def newLogger(id: SystemAgentId, collection: BSONCollection)
                              (implicit afact: ActorRefFactory,
                                        format: ReportLogFormat,
                                        tDesr: TimeDescriptor[Time]) =
    afact.actorOf(Props(new MongoLogger(id, collection)(afact.dispatcher, implicitly, implicitly)))

  def creator(connection: MongoConnection, timeout: Duration)
             (implicit exContext: ExecutionContext,
                       format: ReportLogFormat,
                       tDesr: TimeDescriptor[Time]) =
    AgentCreator(LoggerRole){_ => _ => new ReportDistributedMongoLogger(connection, timeout)}


  implicit object BSONMapHandler extends BSONHandler[BSONDocument, Map[String, BSONValue]] {
    def read(bson: BSONDocument): Map[String, BSONValue] = bson.elements.toMap
    def write(t: Map[String, BSONValue]): BSONDocument = BSONDocument(t)
  }

  def reportDocumentWriter(format: ReportLogFormat, tDescr: TimeDescriptor[Time]): BSONDocumentWriter[Report] =
    new BSONDocumentWriter[Report]{

      def write(t: Report) = t match {
        case tr: TimetableReport => writeTimeTable(tr)
        case r => writeDefault(r)
      }

      def writeClass(c: Class[_]) = BSONDocument(
        "discipline"      -> c.discipline.name
      , "discipline-code" -> c.discipline.code
      , "group"     -> c.group.uniqueId
      , "professor" -> c.professor.uniqueId
      , "classroom" -> c.classroom.uniqueId
      )

      def writeTimeTable(t: TimetableReport) = {
        val mp = t.tt.asMap.mapValues(_.collect{case (time, Some(clazz)) => time -> clazz})
        BSONDocument(
          mp.toSeq.sortBy(_._1).map{
             case (k, v) =>
               val mp = v.toSeq.sortBy(_._1.discrete).map{
                 case (time, clazz) =>
                   tDescr.hr(time) -> writeClass(clazz)
               }
               k.toString -> BSONMapHandler.write(TreeMap(mp: _*))
           } ++ Seq(
            "_id"  -> BSONString(t.uuid.toString)
            , "type" -> BSONString("Timetable")
            , "sender"      -> BSONString(t.sender.id.name)
            , "sender-role" -> BSONString(t.sender.id.role.toString)
            , "isEmpty"     -> BSONBoolean(mp.forall(_._2.isEmpty))
          )
        )
      }

      def writeDefault(t: Report) = BSONDocument( "_id"         -> t.uuid.toString
                                                , "sender"      -> t.sender.id.name
                                                , "sender-role" -> t.sender.id.role.toString
                                                , "type"        -> t.tpe
                                                , "report"      -> t.asString
                                                , "time"        -> System.nanoTime()
                                                , "msg-type"    -> t.underlyingMessage.map(_.tpe).getOrElse("")
      )

  }
}