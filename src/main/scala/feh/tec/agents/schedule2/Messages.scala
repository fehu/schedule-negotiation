package feh.tec.agents.schedule2

import feh.tec.agents.comm._
import feh.tec.agents.schedule2.ExternalKnowledge.{ClassProposal, ConcreteProposal}
import feh.util.{InUnitInterval, UUIDed}

//
///**
//  * A lightweight version of [[feh.tec.agents.comm.NegotiationMessage]]
//  */
//trait Messages extends Message{
//
//}

abstract class AMessage[A](val tpe: String)(implicit val sender: NegotiatingAgentRef) extends UUIDed with NegotiationMessage{
  val value: A
  val negotiation = NegotiationId.Single
  val asString = value.toString
}

trait ProposalMsg { self: AMessage[_] => }
//trait OpinionMsg { self: AMessage[_] => }
trait ArgueMsg { self: AMessage[_] => }
//trait ResponseMsg {
//  self: AMessage[_] =>
//
//  val respondingTo: UUID
//}



case class ClassProposalMsg(value: ConcreteProposal[_])(implicit _sender: NegotiatingAgentRef)
  extends AMessage[ConcreteProposal[_]]("ClassProposal") with ProposalMsg

case class AbstractProposalMsg(value: Discipline, nStudents: Int)(implicit _sender: NegotiatingAgentRef)
  extends AMessage[Discipline]("AbstractProposal") with ProposalMsg

case class OpinionMsg(value: Set[ConcreteProposal[_]])(implicit _sender: NegotiatingAgentRef)
  extends AMessage[Set[ConcreteProposal[_]]]("Opinion")





case class CoherenceMsg(value: InUnitInterval)(implicit _sender: NegotiatingAgentRef)
  extends AMessage[InUnitInterval]("Coherence") //with ResponseMsg , respondingTo: UUID

case class YesNoMsg(value: Boolean)(implicit _sender: NegotiatingAgentRef)
  extends AMessage[Boolean]("Coherence") //with ResponseMsg , respondingTo: UUID

case class NotEnoughInfo(implicit _sender: NegotiatingAgentRef)
  extends AMessage[Nothing]("your message've been ignored"){ lazy val value: Nothing = ??? }


