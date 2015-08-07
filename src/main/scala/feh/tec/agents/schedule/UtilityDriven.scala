package feh.tec.agents.schedule

import feh.tec.agents.comm.NegotiatingAgent
import feh.tec.agents.comm.negotiations.Proposals.NegotiationProposal
import feh.tec.agents.schedule.Messages.ClassesProposalMessage
import feh.util._

trait UtilityDriven extends UtilityDrivenGoal{
  agent: NegotiatingAgent with CommonAgentDefs =>



}


trait UtilityDrivenGoal extends UtilityDrivenAgent{
  agent: NegotiatingAgent with CommonAgentDefs =>

  type GoalHolder = MutableTimetable[Class[Time]]

  protected def currentGoalHolder = ???

  protected def assumeProposal(gh: GoalHolder, proposal: NegotiationProposal) = proposal match {
    case prop: ClassesProposalMessage[Time] => gh.copy $$ (putClassIn(prop, _))
  }

  protected def goal(gh: GoalHolder) = ???
}


object UtilityDrivenGoal{

}