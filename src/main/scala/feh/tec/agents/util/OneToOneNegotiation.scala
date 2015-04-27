package feh.tec.agents.util

import feh.tec.agents.comm.{NegotiatingAgentRef, NegotiationVar, Negotiation}

trait OneToOneNegotiation extends Negotiation{
  defineVar(OneToOneNegotiation.NegotiatingWith)
}

object OneToOneNegotiation{
  case object NegotiatingWith extends NegotiationVar{ type T = NegotiatingAgentRef }
}