package feh.tec.agents.schedule2

import akka.actor.{Props, ActorRefFactory}
import akka.util.Timeout
import feh.tec.agents.comm.{NegotiatingAgentId, SystemAgentRef}
import feh.tec.agents.schedule2.GenericCoherenceDrivenAgent.Capacity.CanTeach
import feh.util.InUnitInterval


class Professor(actorFactory: ActorRefFactory,
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

  def canTeach(d: Discipline): Boolean = internalKnowledge.capacities.exists{
    case CanTeach(ds) => ds contains d
    case _            => false
  }

  def canBeAccepted(value: Discipline, nStudents: Int): Boolean = canTeach(value)

  def canBeAccepted(prop: ExternalKnowledge.ConcreteProposal[CoherenceDrivenAgentImpl]): Boolean =
    canTeach(prop.discipline) // TODO: part-time working hours
}


object Professor{

  class Creator(val actorFactory: ActorRefFactory, val timeDescriptor: TimeDescriptor[DTime],
                val reportTo: SystemAgentRef,
                val internalTimeout: Timeout, val externalTimeout: Timeout, val decisionMakingTimeout: Timeout,
                val externalSatisfactionThreshold: () => InUnitInterval.Including,
                val preferencesThreshold: () => InUnitInterval.Including)
    extends AgentCreator[Professor]
  {

    protected def createInternal(id: NegotiatingAgentId, internalKnowledge: Knowledge, relations: AgentRelations) = Props{
      new Professor(actorFactory, id, timeDescriptor, reportTo, internalTimeout, externalTimeout,
        decisionMakingTimeout, internalKnowledge, relations, externalSatisfactionThreshold, preferencesThreshold)
    }
  }
}