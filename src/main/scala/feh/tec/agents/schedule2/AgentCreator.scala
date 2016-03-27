package feh.tec.agents.schedule2

import akka.actor.{Props, ActorRefFactory}
import akka.util.Timeout
import feh.tec.agents.comm.{NegotiatingAgentRef, NegotiatingAgentId, SystemAgentRef, Agent}


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
             relations: AgentRelations): NegotiatingAgentRef =
  {
    val ref = actorFactory.actorOf(createInternal(id, internalKnowledge, relations))
    NegotiatingAgentRef(id, ref)
  }

  protected def createInternal(id: NegotiatingAgentId, internalKnowledge: Knowledge, relations: AgentRelations): Props
}

object AgentContextRelations{

}

/** Relations definition for agent's context. */
case class AgentContextRelations[C <: Coherence.Context[C]](whole: Set[C#RelationWhole],
                                                            binaryWithin: Set[C#RelationBinary],
                                                            binaryWithDefaultGraph: Set[C#RelationBinary])

case class AgentRelations(obligations: AgentContextRelations[Coherence.Contexts.Obligations],
                          preferences: AgentContextRelations[Coherence.Contexts.Preferences]
                         )