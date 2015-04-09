package feh.tec.agents.schedule

import feh.tec.agents.comm.NegotiationVar.Scope
import feh.tec.agents.comm._
import feh.tec.agents.comm.agent.{NegotiationReactionBuilder, Negotiating}
import feh.tec.agents.comm.negotiations._
import Establishing._
import scala.collection.mutable
import feh.util._

class GroupAgent( val id      : NegotiatingAgentId
                , val reportTo: SystemAgentRef
                , val toAttend: GroupAgent.DisciplinesToAttend
                  )
  extends NegotiatingAgent
  with NegotiationReactionBuilder
  with CommonAgentDefs
  with GroupAgentNegotiationPropositionsHandling
{
  def messageReceived: PartialFunction[Message, Unit] = handleNewNegotiations

  def stop(): Unit = ???

  def startNegotiatingOver(d: Discipline) = ???




  def start(): Unit = {
    startSearchingProfessors()
  }

}

object GroupAgent{
  type MinutesPerWeek = Int

  type DisciplinesToAttend = Map[Discipline, MinutesPerWeek]

  object Role extends NegotiationRole("Group")
}

trait GroupAgentNegotiationPropositionsHandling extends Negotiating.DynamicNegotiations {
  agent: NegotiatingAgent with NegotiationReactionBuilder with CommonAgentDefs =>

  def toAttend: GroupAgent.DisciplinesToAttend

  def startNegotiatingOver(d: Discipline)



  def startSearchingProfessors() =
    SharedNegotiation.set(NegVars.NewNegAcceptance)(mutable.Map(searchProfessors().toSeq: _*))

  def searchProfessors() = toAttend map {
    case (disc, _) =>
      val refsMap = SharedNegotiation(Scope).withFilter(_.id.role == ProfessorAgent.Role).map{
        profRef =>
          profRef ! negotiationProposition(Vars.Discipline -> disc)
          profRef.id -> Option.empty[Boolean]
      }

      disc -> refsMap.toMap
    }


  def handleNewNegotiations: PartialFunction[Message, Unit] = {
    case msg: NegotiationProposition => sys.error("todo: recall")   // todo: recall

    case (msg: NegotiationAcceptance) & AwaitingResponse() & WithDiscipline(d) =>
      add _ $ mkNegotiationWith(msg.sender, d)
      modifyNewNegAcceptance(true, msg)
      ifAllResponded(d, startNegotiatingOver(d))

    case (msg: NegotiationRejection) & AwaitingResponse() & WithDiscipline(d) =>
//      val d = msg get Vars.Discipline
      modifyNewNegAcceptance(false, msg)
      ifAllResponded(d, startNegotiatingOver(d))
  }

  private def modifyNewNegAcceptance(b: Boolean, msg: NegotiationEstablishingMessage) = {
    val d = msg.get(Vars.Discipline)
    val par = msg.sender.id -> Some(b)
    SharedNegotiation.transform(NegVars.NewNegAcceptance)( _ $${ _ <<= (d, _.get + par) }  )
  }

  private def ifAllResponded(d: Discipline, f: => Unit) =
    if(SharedNegotiation(NegVars.NewNegAcceptance)(d).forall(_._2.nonEmpty)) f

}