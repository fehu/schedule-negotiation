package feh.tec.agents.schedule

import feh.tec.agents.comm.NegotiatingAgent
import feh.tec.agents.schedule.CommonAgentDefs.PutClassesInterface
import feh.tec.agents.schedule.Messages.ClassesProposalMessage
import feh.util._

trait UtilityDriven extends UtilityDrivenGoal{
  agent: NegotiatingAgent with CommonAgentDefs
                          with CommonAgentProposalAssessment=>


  type ProposalType = ClassesProposalMessage[Time]

  protected def currentGoalHolder = timetable

  protected def acceptProposal(prop: ProposalType) = acceptance(prop, negotiation(prop))
  protected def rejectProposal(prop: ProposalType) = negotiationRejection(negotiation(prop)(NegVars.Discipline))

  def satisfiesConstraints(proposal: ProposalType) = ???
}


trait Preferences extends AgentPreferences {
  agent: NegotiatingAgent =>

  type NegotiationTime = this.type

  def preference(time: NegotiationTime, gh: GoalHolder, proposal: ProposalType) = ???
}

trait UnconditionalPreferences extends AgentUnconditionalPreferences{
  agent: NegotiatingAgent =>

}

trait UtilityDrivenGoal extends UtilityDrivenAgent{
  agent: NegotiatingAgent with PutClassesInterface =>

  type GoalHolder = MutableTimetable[Class[Time]]


  protected def assumeProposal(gh: GoalHolder, proposal: ProposalType) = proposal match {
    case prop: ClassesProposalMessage[Time] => gh.copy $$ (putClassIn(prop, _))
  }

//  protected def goal(gh: GoalHolder) = ???
}


object UtilityDrivenGoal{

}