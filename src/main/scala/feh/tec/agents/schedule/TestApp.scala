package feh.tec.agents.schedule

import akka.actor.{Props, ActorSystem}
import feh.tec.agents.comm._
import feh.tec.agents.schedule.CommonAgentDefs.Timeouts
import feh.util.Path./
import feh.util._
import scala.concurrent.duration._

object TestApp extends App{

  implicit lazy val policy = SchedulePolicy( partTimeStrictlyAfterFullTime = true
                                           , maxStudentsInGroup = 20
                                          )

  lazy val disciplinesSelection = ReadDisciplinesSelections.fromXLS(
    path      = / / "home" / "fehu" / "study" / "tec" / "agents" / "Thesis" / "data" / "Pronosticos EM09_TI.xls",
    sheetName = "Materias"
  )

  lazy val disciplineByCode = disciplinesSelection.keys.map(d => d.code -> d).toMap


  // todo: !!! full-time or part-time ???
  lazy val profsCanTeach = ReadProfsCanTeach.fromXLS(
    / / "home" / "fehu" / "study" / "tec" / "agents" / "Thesis" / "data" / "ProfsCanTeach.xls",
    disciplineByCode
  )

  // todo: !!! full-time or part-time ???
  lazy val professors = profsCanTeach.keySet

  lazy val groups = GroupGenerator.create.divideIntoGroup(disciplinesSelection)


  println("disciplines: " + disciplinesSelection)
  println("profsCanTeach: " + profsCanTeach)
  println("groups: " + groups)

  println("size disciplines: " + disciplinesSelection.size)
  println("size profsCanTeach: " + profsCanTeach.size)
  println("size groups: " + groups.size)



  implicit lazy val asys = ActorSystem.create("test")

  implicit def logFormat = ReportLogFormat.Pretty

  lazy val timeouts = Timeouts(extraScopeTimeout = 2.seconds)

  lazy val reportPrinter = ReportDistributedPrinter.creator("logger", "logs").create("logger")

  def initNegCreators = CoordinatorAgent.InitialNegotiatorsCreators(
    groups = groups.toSeq.map{
                               case (gId, disciplines) =>
                                 val toAttend = disciplines.zipMap(_ => 3*60 /* todo: minutes per week */).toMap
                                 GroupAgent.creator(reportPrinter, toAttend, timeouts)
                             },
    professorsFullTime = profsCanTeach.toSeq.map{
                                            case (id, disciplines) =>
                                              ProfessorAgent.creator(_.FullTime, reportPrinter, disciplines.toSet)
                                       },
    professorsPartTime = Nil // todo: part-time professors
  )

  lazy val controller = CoordinatorAgent.creator(reportPrinter, initNegCreators).create("controller")

    asys actorOf Props(
    new DeafUserAgent(UserAgentId("admin", UserAgentRole("admin")), None,
                      ag => {
                        import ag._
                        controller ! SystemMessage.Start()
                        controller ! SystemMessage.Initialize()
                        Thread sleep 300
                        controller ! ControllerMessage.Begin()

                        val cntrl = ActorRefExtractor(controller).actorRef
                        asys.scheduler.scheduleOnce(20 seconds span, cntrl, SystemMessage.Stop())(asys.dispatcher)
                      })
  )

  Thread.sleep(30*1000)
  asys.awaitTermination(10.seconds)
  sys.exit()
}
