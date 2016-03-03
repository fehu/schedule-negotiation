package feh.tec.agents.schedule2

import feh.tec.agents.comm.NegotiationRole
import feh.tec.agents.schedule2.ExternalKnowledge.ConcreteProposal

/** Messages to be used inside an agent. */
trait Command

object Command{

  case object MakeDecision extends Command
  case object Finished_? extends Command


  case class AddProposal [Time](prop: ConcreteProposal[Time]) extends Command
  case object GetProposals extends Command
  case class GetCounterpartsCount(discipline: Discipline, role: NegotiationRole) extends Command


}
