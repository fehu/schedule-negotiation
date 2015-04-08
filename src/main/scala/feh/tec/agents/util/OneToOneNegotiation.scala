package feh.tec.agents.util

import feh.tec.agents.comm.{AgentRef, NegotiationVar, Negotiation}

trait OneToOneNegotiation extends Negotiation{
  defineVar(OneToOneNegotiation.NegotiatingWith)
}

object OneToOneNegotiation{
  case object NegotiatingWith extends NegotiationVar{ type T = AgentRef }
}