package feh.tec.agents.schedule

import feh.tec.agents.comm.{NegotiationId, Negotiation, NegotiationMessage, NegotiatingAgent}
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
  def absoluteUtility(gh: UnconditionalPreferenceHolder): Double
}


trait AgentUtility extends AgentGoal with AgentPreferences{
  agent: NegotiatingAgent =>

  def satisfiesConstraints(proposal: ProposalType): Boolean

  protected def deltaGoal(before: GoalHolder, after: GoalHolder) = goalAchievement(after) - goalAchievement(before)

  protected def assumeProposal(gh: GoalHolder, proposal: ProposalType): GoalHolder

  protected def weightedPriority(proposal: ProposalType): Double

  def utility(time: NegotiationTime, gh: GoalHolder, proposal: ProposalType) =
    if(satisfiesConstraints(proposal))
      deltaGoal(gh, assumeProposal(gh, proposal)) match {
        case d if d <= 0 => 0d
        case d => d * (weightedPriority(proposal) + preference(time, gh, proposal))
      }
    else 0d

}

trait UtilityDrivenAgent extends AgentUtility{
  agent: NegotiatingAgent =>

  def negotiationTime: NegotiationTime

  protected def currentGoalHolder: GoalHolder

  def utilityAcceptanceThreshold(neg: Negotiation): Double
  def utilityAcceptanceThreshold(neg: NegotiationId): Double

  type MessageType <: NegotiationMessage

  protected def acceptProposal(prop: ProposalType): MessageType
  protected def rejectProposal(prop: ProposalType): MessageType

  def utilityDrivenProposalHandling(prop: ProposalType) =
    utility(negotiationTime, currentGoalHolder, prop) match {
      case accept if accept >= utilityAcceptanceThreshold(prop.negotiation) =>
        beforeAccept(prop, accept)
        acceptProposal(prop)
      case _ => rejectProposal(prop)
    }

  protected def beforeAccept(prop: ProposalType, utility: Double) = {}
}