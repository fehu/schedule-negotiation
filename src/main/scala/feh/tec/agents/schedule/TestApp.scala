package feh.tec.agents.schedule

import akka.actor.{ActorSystem, Props}
import feh.tec.agents.comm._
import feh.tec.agents.schedule.CommonAgentDefs.Timeouts
import feh.tec.agents.schedule.io.{ReportDistributedMongoLogger, ProfsCanTeach, StudentsSelection}
import feh.util.Path./
import feh.util._
import reactivemongo.api.MongoDriver

import scala.concurrent.duration._

object TestApp extends App{

  implicit lazy val policy = SchedulePolicy( partTimeStrictlyAfterFullTime = true
                                           , maxStudentsInGroup = 20
                                          )

  // todo: !!! full-time or part-time ???
  lazy val profsCanTeach = ProfsCanTeach.read(
    / / "home" / "fehu" / "study" / "tec" / "agents" / "Thesis" / "data" / "schedule.xlsx",
    sheetName = "programacionGrupos"
  ).mapValues(_.filter(_.classes != 0)) // todo: labs

  lazy val (professorsFullTime, professorsPartTime) = profsCanTeach.partition(_._1._2)

  lazy val disciplineByCode = profsCanTeach.values.flatten.map(d => d.code -> d).toMap

  lazy val students = StudentsSelection.read(
    Path.absolute("/home/fehu/study/tec/agents/Thesis/data/schedule.xlsx", '/'),
    sheetName = "alumno-materia"
  )

//  lazy val groups = GroupGenerator.create.divideIntoGroup(disciplinesSelection)


  println("size students: " + students.size)
  println("size disciplines: " + disciplineByCode.size)
  println("size professorsFullTime: " + professorsFullTime.size)
  println("size professorsPartTime: " + professorsPartTime.size)
//  println("size groups: " + groups.size)



  implicit lazy val asys = ActorSystem.create("test")

  implicit def logFormat = ReportLogFormat.Pretty

  lazy val timeouts = Timeouts(extraScopeTimeout = 10.seconds)

  val driver = new MongoDriver
  lazy val logDb = driver.connection(List("localhost"))

  lazy val loggingActors = ActorSystem("logs")

  lazy val reportPrinter = ReportDistributedMongoLogger.creator(logDb, 2000.millis)(loggingActors.dispatcher, implicitly, AgentsTime.tDescr)
                                                       .create("the-logger")(loggingActors)

    //ReportDistributedPrinter.creator("logger", "logs").create("logger")

  def initNegCreators = CoordinatorAgent.InitialNegotiatorsCreators(
    students = students.map{
      case (id, disciplines) => StudentAgent.creator(reportPrinter, id, disciplines.flatMap(disciplineByCode.get)) //todo
                                                                        //todo: flatmap is temporary, because labs are ignored for now
    },
    groups = Nil,
    professorsFullTime = mkProfessors(professorsFullTime, _.FullTime),
    professorsPartTime = mkProfessors(professorsPartTime, _.PartTime)
  )

  private def mkProfessors( profs: Map[(ProfessorId, ProfsCanTeach.IsFullTime), scala.Seq[Discipline]]
                          , role: ProfessorAgent.Role.type => ProfessorAgent.Role
                          ) = profs.toSeq.map{
                              case ((id, _), disciplines) =>
                                ProfessorAgent.creator(role, id, reportPrinter, disciplines.toSet)
                            }

  lazy val controller = CoordinatorAgent.creator(reportPrinter, timeouts, implicitly ,initNegCreators).create("controller")

    asys actorOf Props(
    new DeafUserAgent(UserAgentId("admin", UserAgentRole("admin")), None,
                      ag => {
                        import ag._
                        reportPrinter ! SystemMessage.Start()
                        Thread.sleep(5200)
                        controller ! SystemMessage.Start()
                        controller ! SystemMessage.Initialize()
                        Thread sleep 300
                        controller ! ControllerMessage.Begin()

                        val cntrl = ActorRefExtractor(controller).actorRef
                        asys.scheduler.scheduleOnce(10 seconds span, cntrl, GroupAgent.StartSearchingProfessors())(asys.dispatcher)
                        asys.scheduler.scheduleOnce(2 minutes span, cntrl, SystemMessage.Stop())(asys.dispatcher)
                        asys.scheduler.scheduleOnce(3 minutes span, ActorRefExtractor(reportPrinter).actorRef, SystemMessage.Stop())(asys.dispatcher)

                      })
  )

  Thread.sleep(3*60*1000)
  println("stopping")
  asys.awaitTermination(5.minutes)
  sys.exit()
}
