package feh.tec.agents.util

import feh.tec.agents.comm._

case class OneToOneNegotiationId(fst: AgentId, snd: AgentId) extends NegotiationId{
  def name: String = fst.name + " -- " + snd.name

  override def equals(obj: scala.Any): Boolean = canEqual(obj) && (obj match {
    case OneToOneNegotiationId(fst2, snd2) => fst == fst2 && snd == snd2 || fst == snd2 && snd == fst2
  })

  override def hashCode(): Int = math.abs(fst.hashCode() + snd.hashCode()) // todo ???
}

trait OneToOneNegotiation extends Negotiation{
  defineVar(OneToOneNegotiation.NegotiatingWith)
}

object OneToOneNegotiation{
  case object NegotiatingWith extends NegotiationVar{ type T = NegotiatingAgentRef }
}