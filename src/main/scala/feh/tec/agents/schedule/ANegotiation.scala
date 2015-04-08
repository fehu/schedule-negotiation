package feh.tec.agents.schedule

import feh.tec.agents.comm.Negotiation
import feh.tec.agents.comm.negotiations.{DomainsIterating, Issues, Proposals}
import feh.tec.agents.util.OneToOneNegotiation

trait ANegotiation extends Negotiation
  with OneToOneNegotiation
  with Proposals.Negotiation
  with Issues.Negotiation
  with DomainsIterating.Negotiation
