package feh.tec.agents.schedule2

import akka.actor.{Props, ActorRefFactory}
import akka.util.Timeout
import feh.tec.agents.comm.{SystemAgentRef, NegotiatingAgentId}
import feh.tec.agents.schedule2.GenericCoherenceDrivenAgent.Capacity.SearchesDisciplines
import feh.util.InUnitInterval
import feh.util.InUnitInterval.Including


class Group(actorFactory: ActorRefFactory,
            val id: NegotiatingAgentId,
            implicit val timeDescriptor: TimeDescriptor[DTime],
            val reportTo: SystemAgentRef,
            val internalTimeout: Timeout,
            val externalTimeout: Timeout,
            val decisionMakingTimeout: Timeout,
            val internalKnowledge: Knowledge,
            val relations: AgentRelations,
            val externalSatisfactionThreshold: () => InUnitInterval.Including,
            val preferencesThreshold: () => InUnitInterval.Including
           )
  extends CoherenceDrivenAgentImpl(actorFactory) with GenericCoherenceDrivenAgent
{

  def searchesFor(value: Discipline): Boolean = internalKnowledge.capacities.exists{
    case SearchesDisciplines(ds) => ds contains value
    case _                       => false
  }

  def canBeAccepted(value: Discipline, nStudents: Int) = searchesFor(value)
  def canBeAccepted(prop: ExternalKnowledge.ConcreteProposal[CoherenceDrivenAgentImpl]) = searchesFor(prop.discipline)
}

object Group{

  class Creator(val actorFactory: ActorRefFactory, val timeDescriptor: TimeDescriptor[DTime],
                val reportTo: SystemAgentRef,
                val internalTimeout: Timeout, val externalTimeout: Timeout, val decisionMakingTimeout: Timeout)
    extends AgentCreator[Professor]
  {


    protected def createInternal(id: NegotiatingAgentId, internalKnowledge: Knowledge, relations: AgentRelations,
                                 externalSatisfactionThreshold: () => Including,  preferencesThreshold: () => Including) =
      Props( new Group(actorFactory, id, timeDescriptor, reportTo, internalTimeout, externalTimeout,
        decisionMakingTimeout, internalKnowledge, relations, externalSatisfactionThreshold, preferencesThreshold))
  }
}