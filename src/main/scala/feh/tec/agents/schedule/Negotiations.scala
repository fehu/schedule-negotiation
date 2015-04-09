package feh.tec.agents.schedule

import feh.tec.agents.comm.{NegotiationVar, NegotiationId, Negotiation}
import feh.tec.agents.comm.negotiations.{DomainsIterating, Issues, Proposals}
import feh.tec.agents.util.OneToOneNegotiation

trait ANegotiation extends Negotiation
  with OneToOneNegotiation
  with Proposals.Negotiation
  with Issues.Negotiation
  with DomainsIterating.Negotiation
{
  def discipline: Discipline
}


abstract class SharedNegotiation(varUpdated: Negotiation.VarUpdated[_ <: NegotiationVar] => Unit)
  extends Negotiation(SharedNegotiation.id, varUpdated)

object SharedNegotiation{
  lazy val id = NegotiationId("Shared")
}