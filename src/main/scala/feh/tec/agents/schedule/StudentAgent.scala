package feh.tec.agents.schedule

import feh.tec.agents.comm.agent.Negotiating.DynamicNegotiations
import feh.tec.agents.comm.agent.NegotiationReactionBuilder
import feh.tec.agents.comm._
import feh.tec.agents.schedule.io.StudentsSelection
import feh.tec.agents.util.OneToOneNegotiationId
import feh.util.UUIDed

class StudentAgent( val id          : NegotiatingAgentId
                  , val studentId   : StudentId
                  , val reportTo    : SystemAgentRef
                  , val coordinator : AgentRef
                  , val toAttend    : Seq[Discipline])
  extends NegotiatingAgent
  with NegotiationReactionBuilder
  with CommonAgentDefs
  with DynamicNegotiations
{
  protected def negotiationWithId(withAg: NegotiatingAgentRef): NegotiationId = OneToOneNegotiationId(this.id, withAg.id)

  override lazy val Reporting = new ReportingNegotiationsConfig(messageSent = true, messageReceived = true)

  def messageReceived: PartialFunction[Message, Unit] = ???

  def start(): Unit = coordinator ! StudentAgent.AssignMeToGroups(studentId, toAttend)
  def stop(): Unit = ???
}

object StudentAgent{
  type MinutesPerWeek = StudentsSelection.MinutesPerWeek

  type Career = String

  object Role extends NegotiationRole("Student")


  def creator(reportTo: SystemAgentRef, studentId: StudentId, toAttend: Seq[Discipline]) =
    new NegotiatingAgentCreator[StudentAgent](Role, scala.reflect.classTag[StudentAgent],
                                              id => {
                                                case Some(creator) => new StudentAgent(id, studentId, reportTo, creator, toAttend)
                                              })



  case class AssignMeToGroups(studentId: StudentId, disciplines: Seq[Discipline])
                             (implicit val sender: AgentRef) extends UUIDed with Message
  {
    val tpe = "Assign me to groups"
    val asString = studentId + ": " + disciplines.mkString(", ")
  }


  case class AddedToGroup(discipline: Discipline, groupRef: NegotiatingAgentRef)
                         (implicit val sender: AgentRef) extends UUIDed with Message
  {
    val tpe = "You've been added to group"
    val asString = discipline.toString + " -- " + groupRef.toString
  }

  case class RemovedFromGroup(discipline: Discipline, groupRef: NegotiatingAgentRef)
                             (implicit val sender: AgentRef) extends UUIDed with Message
  {
    val tpe = "You've been removed from group"
    val asString = discipline.toString + " -- " + groupRef.toString
  }
}