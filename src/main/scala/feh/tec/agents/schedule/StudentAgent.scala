package feh.tec.agents.schedule

import akka.actor.ActorLogging
import feh.tec.agents.comm.agent.Negotiating.DynamicNegotiations
import feh.tec.agents.comm.agent.NegotiationReactionBuilder
import feh.tec.agents.comm._
import feh.tec.agents.schedule.CommonAgentProposal.DefaultAssessor
import feh.tec.agents.schedule.Discipline.MinutesPerWeek
import feh.tec.agents.schedule.io.StudentsSelection
import feh.tec.agents.util.OneToOneNegotiationId
import feh.util.{InUnitInterval, UUIDed}

class StudentAgent( val id          : NegotiatingAgentId
                  , val thisIdVal   : StudentId
                  , val reportTo    : SystemAgentRef
                  , val coordinator : AgentRef
                  , val toAttend    : Seq[Discipline])
  extends NegotiatingAgent
  with NegotiationReactionBuilder
  with CommonAgentDefs
//  with CommonAgentProposalAssessment
//  with UtilityDriven
//  with DefaultAssessor
  with DynamicNegotiations
  with ActorLogging
{

  type ThisId = StudentId
  def thisIdVar = NegVars.StudentId

  protected def negotiationWithId(withAg: NegotiatingAgentRef): NegotiationId = OneToOneNegotiationId(this.id, withAg.id)

  def messageReceived: PartialFunction[Message, Unit] = Map()

  def start(): Unit = coordinator ! StudentAgent.AssignMeToGroups(thisIdVal, toAttend)
  def stop(): Unit = {
    reportTimetable()
    context.stop(self)
  }

/*
  // the goal is finding classes for all disciplines, with the required duration // todo: labs
  def goalAchievement(gh: GoalHolder) = {
    val assignedClassesDurations = ImmutableTimetable.filterEmpty(gh.asMap)
                                   .mapValues(classesDuration)
                                   .values.flatten
                                   .groupBy(_._1)
                                   .mapValues(_.map(_._2).sum)
    val remainsW = for {
      disc <- toAttend
      durC = disc.classes
      durL = disc.labs // todo: labs
    } yield disc -> (durC - assignedClassesDurations
                            .filter(_._1.discipline == disc)
                            .map(_._2).sum
        ) / durC

    InUnitInterval(remainsW.map(_._2).sum / remainsW.length)
  }
*/

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