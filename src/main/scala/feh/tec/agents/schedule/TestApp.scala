package feh.tec.agents.schedule

import akka.actor.{Props, ActorSystem}
import feh.tec.agents.comm._
import feh.util.Path./

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



  implicit lazy val asys = ActorSystem.create("test")

  implicit def logFormat = ReportLogFormat.Pretty

  lazy val reportPrinter = ReportDistributedPrinter.creator("logger", "logs").create("logger")

  def initNegCreators: CoordinatorAgent.InitialNegotiatorsCreators = ???

  lazy val controller = CoordinatorAgent.creator(reportPrinter, initNegCreators).create("controller")

  asys actorOf Props(
    new DeafUserAgent(UserAgentId("admin", UserAgentRole("admin")), None,
                      ag => {
                        import ag._
                        controller ! SystemMessage.Start()
                        Thread sleep 300
                        controller ! CoordinatorAgent.Begin()
                      })
  )
}
