package feh.tec.agents.schedule

import akka.util.Timeout
import feh.tec.agents.comm.Message.HasValues
import feh.tec.agents.comm.Negotiation.VarUpdated
import feh.tec.agents.comm.Report.StateChanged
import feh.tec.agents.comm._
import feh.tec.agents.comm.agent.Negotiating.DynamicNegotiations
import feh.tec.agents.comm.negotiations.Establishing.{NegotiationEstablishingMessage, NegotiationProposition, NegotiationAcceptance, NegotiationRejection}
import feh.tec.agents.comm.negotiations.Var
import feh.tec.agents.util.OneToOneNegotiation

trait CommonAgentDefs {
  agent: NegotiatingAgent with DynamicNegotiations =>

  type Time

  /** a negotiation that is easily accessed from others negotiations */
  lazy val SharedNegotiation = new SharedNegotiation(varUpdatedNotification) {
    defineVar(NegVars.NewNegAcceptance)
  }

  add(SharedNegotiation)
  
  override lazy val Reporting = new ReportingNegotiationsConfig(messageReceived = true, messageSent = true)

  protected def varUpdatedNotification(upd: VarUpdated[_ <: NegotiationVar]) = reportTo ! StateChanged(upd)



  protected def negotiationWithId(withAg: NegotiatingAgentRef) = NegotiationId(withAg.id.name + " -- " + this.id.name)

  protected def mkNegotiationWith(withAg: NegotiatingAgentRef, disc: Discipline): Negotiation =
    new Negotiation(negotiationWithId(withAg), varUpdatedNotification) with ANegotiation[Time] {
      def discipline = disc

      set(NegVars.Discipline)(disc)
      set(OneToOneNegotiation.NegotiatingWith)(withAg)
    }


  def negotiationProposition(vals: (Var[Any], Any)*) = new NegotiationProposition{
    val values = vals.toMap
    override val sender: NegotiatingAgentRef = implicitly
  }
  
  def negotiationRejection(d: Discipline)(implicit snd: NegotiatingAgentRef) = new NegotiationRejection{
    val values: Map[Var[Any], Any] = Map(Vars.Discipline -> d)
    override val sender = snd
  }

  def negotiationAcceptance(d: Discipline) = new NegotiationAcceptance {
    val values: Map[Var[Any], Any] = Map(Vars.Discipline -> d)
    override val sender: NegotiatingAgentRef = implicitly
  }

  object Tst{
    def unapply(msg: Message): Option[Boolean] = Some(true)
  }

  object WithDiscipline{
    def unapply(msg: Message): Option[Discipline] = msg match {
      case msg: NegotiationEstablishingMessage =>
        implicitly[HasValues[NegotiationEstablishingMessage]].valueOpt(msg)(Vars.Discipline)
    }
  }

}

object CommonAgentDefs{
  case class Timeouts( extraScopeTimeout: Timeout
                       )

  def counterpartOpt(neg: Negotiation) = neg.get(OneToOneNegotiation.NegotiatingWith)
  def counterpart(neg: Negotiation)    = counterpartOpt(neg).get

  def disciplineOpt(neg: Negotiation)  = neg.get(NegVars.Discipline)
  def discipline(neg: Negotiation)     = disciplineOpt(neg).get

}