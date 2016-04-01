package feh.tec.agents.test

import akka.actor.ActorSystem
import feh.tec.agents.comm.SystemMessage.Start
import feh.tec.agents.comm.{DeafUserAgent, ReportDistributedPrinter}
import feh.tec.agents.schedule2.{Group, Professor}
import feh.tec.agents.test.data.{Common, Groups, Professors}
import feh.tec.agents.comm.ReportLogFormat.Pretty

import scala.concurrent.duration._

object Run extends App{

  implicit val asys = ActorSystem.create

  val reporter = ReportDistributedPrinter.creator("reporter", "reports").create("print")

  val internalTimeout = 50 millis span
  val externalTimeout = 20 seconds span
  val decisionTimeout = 1 minute span


  val profCr = new Professor.Creator(asys, Common.timeDescriptor, reporter, internalTimeout, externalTimeout, decisionTimeout)
  val profs = Professors.create(profCr)

  val groupsCr = new Group.Creator(asys, Common.timeDescriptor, reporter, internalTimeout, externalTimeout, decisionTimeout)
  val groups = Groups.create(groupsCr)

  val agents = profs ++ groups


  DeafUserAgent.creator("user", {
    ag =>
      import ag._
      agents foreach {
          _ ! Start()
      }
  }).create("init")

}
