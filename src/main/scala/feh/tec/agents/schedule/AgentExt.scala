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

  def preference(time: NegotiationTime, gh: GoalHolder, proposal: NegotiationProposal): InUnitInterval

}


trait AgentUnconditionalPreferences{
  agent: NegotiatingAgent =>

  type UnconditionalPreferenceHolder

  def absolutePreference(ph: UnconditionalPreferenceHolder): InUnitInterval
}


trait AgentUtility extends AgentGoal with AgentPreferences{
  agent: NegotiatingAgent =>

  def satisfiesConstraints(proposal: NegotiationProposal): Boolean

  protected def deltaGoal(before: GoalHolder, after: GoalHolder): InUnitInterval

  protected def assumeProposal(gh: GoalHolder, proposal: NegotiationProposal): GoalHolder

  protected def weightedPriority(proposal: NegotiationProposal): Double

  def utility(time: NegotiationTime, gh: GoalHolder, proposal: NegotiationProposal) =
    if(satisfiesConstraints(proposal))
      deltaGoal(gh, assumeProposal(gh, proposal)) * (weightedPriority(proposal) + preference(time, gh, proposal))
    else 0d

}

trait UtilityDrivenAgent extends AgentUtility{
  agent: NegotiatingAgent =>

  def negotiationTime: NegotiationTime

  protected def currentGoalHolder: GoalHolder

  def utilityAcceptanceThreshold: Double

  protected def acceptProposal(prop: NegotiationProposal)
  protected def rejectProposal(prop: NegotiationProposal)

  def utilityDrivenProposalHandling(prop: NegotiationProposal) =
    utility(negotiationTime, currentGoalHolder, prop) match {
      case accept if accept >= utilityAcceptanceThreshold => acceptProposal(prop)
      case _                                              => rejectProposal(prop)
    }
}