package feh.tec.agents.schedule

import feh.tec.agents.comm.Message.HasValues
import feh.tec.agents.comm.Negotiation.VarUpdated
import feh.tec.agents.comm.Report.StateChanged
import feh.tec.agents.comm._
import feh.tec.agents.comm.negotiations.Establishing.{NegotiationEstablishingMessage, NegotiationProposition, NegotiationAcceptance, NegotiationRejection}

trait CommonAgentDefs {
  agent: NegotiatingAgent =>

  /** a negotiation that is easily accessed from others negotiations */
  lazy val SharedNegotiation = new SharedNegotiation(varUpdatedNotification) { }
  
  override lazy val Reporting = new ReportingNegotiationsConfig //(messageReceived = true, messageSent = true)

  protected def varUpdatedNotification(upd: VarUpdated[_ <: NegotiationVar]) = reportTo ! StateChanged(upd)



  protected def negotiationWithId(withAg: NegotiatingAgentRef) = NegotiationId(withAg.id.name + " -- " + this.id.name)

  protected def mkNegotiationWith(withAg: NegotiatingAgentRef, disc: Discipline): Negotiation =
    new Negotiation(negotiationWithId(withAg), varUpdatedNotification) with ANegotiation { def discipline = disc }



  def negotiationProposition(vals: (Var[Any], Any)*) = new NegotiationProposition{
    val values = vals.toMap
    override val sender: NegotiatingAgentRef = implicitly
  }
  
  def negotiationRejection(implicit snd: NegotiatingAgentRef) = new NegotiationRejection{
    val values = Map.empty[Var[Any], Any]
    override val sender = snd
  }

  def negotiationAcceptance = new NegotiationAcceptance {
    val values: Map[Var[Any], Any] = Map()
    override val sender: NegotiatingAgentRef = implicitly
  }

  object WithDiscipline{
    def unapply(msg: Message): Option[Discipline] = PartialFunction.condOpt(msg){
      case msg: NegotiationEstablishingMessage =>
        implicitly[HasValues[NegotiationEstablishingMessage]].value(msg)(Vars.Discipline)
    }
  }

}
