package feh.tec.agents.schedule2

import akka.actor.{Props, ActorRefFactory}
import akka.util.Timeout
import feh.tec.agents.comm._
import feh.tec.agents.schedule2.InternalKnowledge.{Preference, Obligation}
import feh.util.InUnitInterval


/**
  * Agents Factory.
  */
trait AgentCreator[A <: CoherenceDrivenAgentImpl] {
  val actorFactory: ActorRefFactory
  val timeDescriptor: TimeDescriptor[DTime]
  val reportTo: SystemAgentRef
  val internalTimeout: Timeout
  val externalTimeout: Timeout

  def create(id: NegotiatingAgentId,
             internalKnowledge: Knowledge,
             relations: AgentRelations,
             externalSatisfactionThreshold: () => InUnitInterval.Including,
             preferencesThreshold: () => InUnitInterval.Including): NegotiatingAgentRef =
  {
    val ref = actorFactory.actorOf(
      createInternal(id, internalKnowledge, relations, externalSatisfactionThreshold, preferencesThreshold)
    )
    NegotiatingAgentRef(id, ref)
  }

  def create(d: AgentDescriptor): NegotiatingAgentRef =
    create(NegotiatingAgentId(d.id, d.role),
      d.internalKnowledge,
      d.relations,
      d.externalSatisfactionThreshold,
      d.preferencesThreshold
    )

  protected def createInternal(id: NegotiatingAgentId, internalKnowledge: Knowledge, relations: AgentRelations,
                               externalSatisfactionThreshold: () => InUnitInterval.Including,
                               preferencesThreshold: () => InUnitInterval.Including): Props
}

/** Relations definition for agent's context. */
case class AgentContextRelations[C <: Coherence.Context[C]](whole: Set[Coherence.RelationWhole[C]],
                                                            binaryWithin: Set[Coherence.RelationBinary[C]],
                                                            binaryWithDefaultGraph: Set[Coherence.RelationBinary[C]])

case class AgentRelations(obligations: AgentContextRelations[Coherence.Contexts.Obligations],
                          preferences: AgentContextRelations[Coherence.Contexts.Preferences]
                         )


case class AgentDescriptor(id: String,
                           role: NegotiationRole,
                           internalKnowledge: Knowledge,
                           relations: AgentRelations,
                           externalSatisfactionThreshold: () => InUnitInterval.Including,
                           preferencesThreshold: () => InUnitInterval.Including
                          )

case class ObligationsContainer(ctx: AgentContextRelations[Coherence.Contexts.Obligations], knowledge: Set[Obligation])
case class PreferencesContainer(ctx: AgentContextRelations[Coherence.Contexts.Preferences], knowledge: Set[Preference])
