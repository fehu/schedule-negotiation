package feh.tec.agents.schedule

import feh.tec.agents.comm.NegotiatingAgent
import feh.tec.agents.comm.negotiations.Proposals.NegotiationProposal
import feh.util.InUnitInterval

trait AgentGoal {
  agent: NegotiatingAgent =>

  type GoalHolder

  def goalAchievement(gh: GoalHolder): InUnitInterval
}


trait AgentPreferences extends AgentGoal{
  agent: NegotiatingAgent =>

  type NegotiationTime

  type ProposalType <: NegotiationProposal

  def preference(time: NegotiationTime, gh: GoalHolder, proposal: ProposalType): InUnitInterval

}


trait AgentUnconditionalPreferences{
  agent: NegotiatingAgent =>

  type UnconditionalPreferenceHolder

  def absolutePreference(ph: UnconditionalPreferenceHolder): InUnitInterval
}


trait AgentUtility extends AgentGoal with AgentPreferences{
  agent: NegotiatingAgent =>

  def satisfiesConstraints(proposal: ProposalType): Boolean

  protected def deltaGoal(before: GoalHolder, after: GoalHolder) = goalAchievement(before) - goalAchievement(after)

  protected def assumeProposal(gh: GoalHolder, proposal: ProposalType): GoalHolder

  protected def weightedPriority(proposal: ProposalType): Double

  def utility(time: NegotiationTime, gh: GoalHolder, proposal: ProposalType) =
    if(satisfiesConstraints(proposal))
      deltaGoal(gh, assumeProposal(gh, proposal)) match {
        case 0 => 0d
        case d => d * (weightedPriority(proposal) + preference(time, gh, proposal))
      }
    else 0d

}

trait UtilityDrivenAgent extends AgentUtility{
  agent: NegotiatingAgent =>

  def negotiationTime: NegotiationTime

  protected def currentGoalHolder: GoalHolder

  def utilityAcceptanceThreshold: Double

  protected def acceptProposal(prop: ProposalType)
  protected def rejectProposal(prop: ProposalType)

  def utilityDrivenProposalHandling(prop: ProposalType) =
    utility(negotiationTime, currentGoalHolder, prop) match {
      case accept if accept >= utilityAcceptanceThreshold => acceptProposal(prop)
      case _                                              => rejectProposal(prop)
    }
}