package feh.tec.agents.schedule

import feh.tec.agents.comm.agent.Negotiating.DynamicNegotiations
import feh.tec.agents.comm.agent.NegotiationReactionBuilder
import feh.tec.agents.comm._
import feh.tec.agents.util.OneToOneNegotiationId

class StudentAgent( val id          : NegotiatingAgentId
                  , val reportTo    : SystemAgentRef
                  , val toAttend    : StudentAgent.DisciplinesToAttend)
  extends NegotiatingAgent
  with NegotiationReactionBuilder
  with CommonAgentDefs
  with DynamicNegotiations
{
  protected def negotiationWithId(withAg: NegotiatingAgentRef): NegotiationId = OneToOneNegotiationId(this.id, withAg.id)

  def messageReceived: PartialFunction[Message, Unit] = ???

  def start(): Unit = ???
  def stop(): Unit = ???
}

object StudentAgent{
  type MinutesPerWeek = Int

  type DisciplinesToAttend = Map[Discipline, MinutesPerWeek]
}