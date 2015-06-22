package feh.tec.agents.schedule

import akka.actor.{ActorSystem, Props}
import feh.tec.agents.comm._
import feh.tec.agents.schedule.CommonAgentDefs.Timeouts
import feh.tec.agents.schedule.io.{ProfsCanTeach, StudentsSelection}
import feh.util.Path./
import feh.util._

import scala.concurrent.duration._

object TestApp extends App{

  implicit lazy val policy = SchedulePolicy( partTimeStrictlyAfterFullTime = true
                                           , maxStudentsInGroup = 20
                                          )

  // todo: !!! full-time or part-time ???
  lazy val profsCanTeach = ProfsCanTeach.read(
    / / "home" / "fehu" / "study" / "tec" / "agents" / "Thesis" / "data" / "schedule.xlsx",
    sheetName = "programacionGrupos"
  )

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

  lazy val timeouts = Timeouts(extraScopeTimeout = 2.seconds)

  lazy val reportPrinter = ReportDistributedPrinter.creator("logger", "logs").create("logger")

  def initNegCreators = CoordinatorAgent.InitialNegotiatorsCreators(
//    groups = groups.toSeq.map{
//                               case (gId, disciplines) =>
//                                 val toAttend = disciplines.zipMap(_ => 3*60 /* todo: minutes per week */ ).toMap
//                                 GroupAgent.creator(reportPrinter, toAttend, timeouts)
//                             },
    students = students.map{
      case (id, disciplines) =>
//        val toAttend = disciplines.flatMap{
//                         case (k, v) => disciplineByName.get(k).map(_ -> v) // todo: not all profs and disciplines exist
//                       }
        val toAttend = disciplines.mapKeys(disciplineByCode)
        StudentAgent.creator(reportPrinter, toAttend)
    },
    groups = Nil,
    professorsFullTime = mkProfessors(professorsFullTime, _.FullTime),
    professorsPartTime = mkProfessors(professorsPartTime, _.PartTime)
  )

  private def mkProfessors( profs: Map[(ProfessorId, ProfsCanTeach.IsFullTime), scala.Seq[Discipline]]
                          , role: ProfessorAgent.Role.type => ProfessorAgent.Role
                          ) = profs.toSeq.map{
                              case ((id, _), disciplines) =>
                                ProfessorAgent.creator(role, reportPrinter, disciplines.toSet)
                            }

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
