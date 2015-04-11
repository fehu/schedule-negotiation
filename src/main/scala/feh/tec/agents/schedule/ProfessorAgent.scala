package feh.tec.agents.schedule

import feh.tec.agents.comm.Negotiation.VarUpdated
import feh.tec.agents.comm.Report.StateChanged
import feh.tec.agents.comm.negotiations.Establishing.itHasValues
import feh.util._
import feh.tec.agents.comm.agent.{NegotiationReactionBuilder, Negotiating}
import feh.tec.agents.comm._
import feh.tec.agents.comm.negotiations.Establishing.{NegotiationEstablishingMessage, NegotiationRejection, NegotiationProposition, NegotiationAcceptance}

class ProfessorAgent( val id: NegotiatingAgentId
                    , val reportTo: SystemAgentRef
                    , val canTeach: Discipline => Boolean
                      )
  extends NegotiatingAgent
  with NegotiationReactionBuilder
  with CommonAgentDefs
  with ProfessorAgentNegotiationPropositionsHandling
{
  def messageReceived: PartialFunction[Message, Unit] = handleNegotiationPropositions

  def start(): Unit = ???
  def stop(): Unit = ???
}

object ProfessorAgent{
  sealed trait Role extends NegotiationRole
  object Role{
    lazy val FullTime = new NegotiationRole("Professor: full-time") with Role
    lazy val PartTime = new NegotiationRole("Professor: part-time") with Role
  }
}

trait ProfessorAgentNegotiationPropositionsHandling
  extends Negotiating.DynamicNegotiations
{
  agent: NegotiatingAgent with NegotiationReactionBuilder with CommonAgentDefs =>

  def canTeach: Discipline => Boolean

  def recallRequested(msg: NegotiationProposition): Message = ???




  def handleNegotiationPropositions: PartialFunction[Message, Unit] = {
    case msg: NegotiationProposition =>
      val disc = getFromMsg(msg, Vars.Discipline)
      val propOrRecall = getFromMsg(msg, Vars.PropOrRecall)

      if (propOrRecall == Vars.New) msg.sender !
        (if (canTeach(disc)) startNegotiationWith(msg.sender, disc) else negotiationRejection)
      else recallRequested(msg)
  }

  /** creates a negotiation and guards it */
  def startNegotiationWith(ag: NegotiatingAgentRef, disc: Discipline): NegotiationAcceptance = {
    add _ $ mkNegotiationWith(ag, disc)
    negotiationAcceptance
  }
}