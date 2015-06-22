package feh.tec.agents.schedule

import feh.tec.agents.comm.agent.Negotiating.DynamicNegotiations
import feh.tec.agents.comm.agent.NegotiationReactionBuilder
import feh.tec.agents.comm._
import feh.tec.agents.schedule.io.StudentsSelection
import feh.tec.agents.util.OneToOneNegotiationId
import feh.util.UUIDed

class StudentAgent( val id          : NegotiatingAgentId
                  , val reportTo    : SystemAgentRef
                  , val toAttend    : Seq[Discipline])
  extends NegotiatingAgent
  with NegotiationReactionBuilder
  with CommonAgentDefs
  with DynamicNegotiations
{
  protected def negotiationWithId(withAg: NegotiatingAgentRef): NegotiationId = OneToOneNegotiationId(this.id, withAg.id)

  def messageReceived: PartialFunction[Message, Unit] = ???

  def start(): Unit = ???
  def stop(): Unit = ???
}

object StudentAgent{
  type MinutesPerWeek = StudentsSelection.MinutesPerWeek

  type Career = String

  object Role extends NegotiationRole("student")


  def creator(reportTo: SystemAgentRef, toAttend: Seq[Discipline]) =
    new NegotiatingAgentCreator[StudentAgent](Role, scala.reflect.classTag[StudentAgent],
                                              id => _ => new StudentAgent(id, reportTo, toAttend))



  case class AssignMeToAGroup(implicit val sender: AgentRef) extends UUIDed with Message{
    val tpe = "Assign me to a group"
    val asString = ""
  }

}