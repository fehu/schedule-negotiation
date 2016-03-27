package feh.tec.agents.schedule2

import akka.actor.ActorRefFactory
import akka.util.Timeout
import feh.tec.agents.comm.{SystemAgentRef, NegotiatingAgentId}
import feh.util.InUnitInterval

class Classroom (actorFactory: ActorRefFactory,
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
  def canBeAccepted(value: Discipline, nStudents: Int): Boolean = ???

  def canBeAccepted(prop: ExternalKnowledge.ConcreteProposal[CoherenceDrivenAgentImpl]): Boolean = ???
}
