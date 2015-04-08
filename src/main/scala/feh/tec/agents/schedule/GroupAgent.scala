package feh.tec.agents.schedule

import feh.tec.agents.comm._
import feh.tec.agents.comm.agent.{NegotiationReactionBuilder, Negotiating}
import feh.tec.agents.comm.negotiations._
import Establishing._

class GroupAgent(val id: NegotiatingAgentId, val reportTo: SystemAgentRef)
  extends NegotiatingAgent
  with Negotiating.DynamicNegotiations
  with NegotiationReactionBuilder
{
  override lazy val Reporting = new ReportingNegotiationsConfig //(messageReceived = true, messageSent = true)

  protected def initializeNegotiations = Nil

  def messageReceived: PartialFunction[Message, Unit] = ???

  def start(): Unit = ???

  def stop(): Unit = ???

  def handleNewNegotiations: PartialFunction[Message, Unit] = {
    case msg: NegotiationProposition =>
  }
}