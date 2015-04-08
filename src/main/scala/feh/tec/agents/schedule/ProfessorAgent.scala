package feh.tec.agents.schedule

import feh.tec.agents.comm.Negotiation.VarUpdated
import feh.util._
import feh.tec.agents.comm.agent.{NegotiationReactionBuilder, Negotiating}
import feh.tec.agents.comm._
import feh.tec.agents.comm.negotiations.Establishing.{NegotiationEstablishingMessage, NegotiationRejection, NegotiationProposition, NegotiationAcceptance}

class ProfessorAgent(val id: NegotiatingAgentId, val reportTo: SystemAgentRef, val canTeach: String => Boolean)
  extends NegotiatingAgent
  with Negotiating.DynamicNegotiations
  with NegotiationReactionBuilder
{
  override val Reporting = new ReportingNegotiationsConfig

  def messageReceived: PartialFunction[Message, Unit] = ???

  def stop(): Unit = ???

  def start(): Unit = ???

  /*
   *  NegotiationPropositions
   */

  def recallRequested(msg: NegotiationProposition): Message = ???

  def handleNegotiationPropositions: PartialFunction[Message, Unit] = {
    case msg: NegotiationProposition =>
      val disc = getFromMsg(msg, Vars.Discipline)
      val propOrRecall = getFromMsg(msg, Vars.PropOrRecall)

      if (propOrRecall == Vars.New) msg.sender !
        (if (canTeach(disc)) startNegotiationWith(msg.sender, disc) else negotiationRejection)
      else recallRequested(msg)
  }


  def negotiationRejection(implicit snd: NegotiatingAgentRef) = new NegotiationRejection{
    val myValues = Map.empty[Var[Any], Any]
    override val sender = snd
  }
  def negotiationAcceptance = new NegotiationAcceptance {
    val myValues: Map[Var[Any], Any] = Map()
    override val sender: NegotiatingAgentRef = implicitly
  }

  protected def varUpdatedNotification(upd: VarUpdated[_]) = ???

  /** creates a negotiation and guards it */
  def startNegotiationWith(ag: AgentRef, disc: String): NegotiationAcceptance = {
    val id = NegotiationId(ag.id.name + " -- " + this.id.name)
    val neg  = new Negotiation(id, varUpdatedNotification) with ANegotiation {}
    _negotiations += id -> neg
    negotiationAcceptance
  }

  /*
   *
   */

  private def getFromMsg[T](msg: NegotiationEstablishingMessage, v: Var[T]) =
    msg.myValues.get(v).getOrThrow(s"${msg.tpe} had no ${v.name} var").asInstanceOf[T]

}
