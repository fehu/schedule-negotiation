package feh.tec.agents.schedule

import akka.util.Timeout
import feh.tec.agents.comm.NegotiationVar.Scope
import feh.tec.agents.comm._
import feh.tec.agents.comm.agent.{NegotiationReactionBuilder, Negotiating}
import feh.tec.agents.comm.negotiations._
import Establishing._
import feh.tec.agents.util.OneToOneNegotiation
import scala.collection.mutable
import feh.util._
import scala.concurrent.duration._

import scala.concurrent.Await

class GroupAgent( val id          : NegotiatingAgentId
                , val coordinator : AgentRef
                , val reportTo    : SystemAgentRef
                , val toAttend    : GroupAgent.DisciplinesToAttend
                , val timeouts    : CommonAgentDefs.Timeouts
                  )
  extends NegotiatingAgent
  with NegotiationReactionBuilder
  with CommonAgentDefs
  with GroupAgentNegotiationPropositionsHandling
  with GroupAgentNegotiating
  with GroupAgentProposals
{
  def messageReceived: PartialFunction[Message, Unit] = handleNewNegotiations

  def askForExtraScope(role: NegotiationRole)(implicit timeout: Timeout): Set[NegotiatingAgentRef] =
    Await.result(
      (coordinator ? CoordinatorAgent.ExtraScopeRequest(role)).mapTo[Set[NegotiatingAgentRef]],
      timeout.duration*1.1
    )

  def extraScopeTimeout = timeouts.extraScopeTimeout

  def start(): Unit = {
    startSearchingProfessors()
  }

  def stop(): Unit = ???
}

object GroupAgent{
  type MinutesPerWeek = Int

  type DisciplinesToAttend = Map[Discipline, MinutesPerWeek]

  object Role extends NegotiationRole("Group")
}

/** Creating new proposals and evaluating already existing
 *
 */
trait GroupAgentProposals{
  def nextProposalFor(d: Discipline): Proposals.Proposal = ???
}

trait GroupAgentNegotiating{
  agent: NegotiatingAgent with NegotiationReactionBuilder with CommonAgentDefs =>

  def nextProposalFor(d: Discipline): Proposals.Proposal

  def negotiationsOver(d: Discipline) = negotiations.filter(_._2.get(NegVars.Discipline) contains d)

  def startNegotiatingOver(d: Discipline): Unit = {
    val counterparts = negotiationsOver(d).flatMap(_._2.get(OneToOneNegotiation.NegotiatingWith))
    val proposal = nextProposalFor(d)
    counterparts foreach (_ ! proposal)
  }
}

trait GroupAgentNegotiationPropositionsHandling extends Negotiating.DynamicNegotiations {
  agent: NegotiatingAgent with NegotiationReactionBuilder with CommonAgentDefs =>

  def toAttend: GroupAgent.DisciplinesToAttend

  def startNegotiatingOver(d: Discipline)

  def askForExtraScope(role: NegotiationRole)(implicit timeout: Timeout): Set[NegotiatingAgentRef]

  def extraScopeTimeout: Timeout

  def startSearchingProfessors() =
    SharedNegotiation.set(NegVars.NewNegAcceptance)(mutable.Map(searchProfessors().toSeq: _*))

  def searchProfessors(profRefs: Set[NegotiatingAgentRef] = SharedNegotiation(Scope).filter(_.id.role.isInstanceOf[ProfessorAgent.Role])) =
    toAttend map {
      case (disc, _) =>
        val refsMap = profRefs.map{
          profRef =>
            profRef ! negotiationProposition(Vars.Discipline -> disc)
            profRef.id -> Option.empty[Boolean]
        }

        disc -> refsMap.toMap
    }

  private var askedForExtraScope = false

  def handleNewNegotiations: PartialFunction[Message, Unit] = {
    case msg: NegotiationProposition => sys.error("todo: recall")   // todo: recall

    case (msg: NegotiationAcceptance) & AwaitingResponse() & WithDiscipline(d) =>
      add _ $ mkNegotiationWith(msg.sender, d)
      modifyNewNegAcceptance(true, msg)
      checkResponsesFor(d, ProfessorAgent.Role.PartTime)(extraScopeTimeout)

    case (msg: NegotiationRejection) & AwaitingResponse() & WithDiscipline(d) =>
      modifyNewNegAcceptance(false, msg)
      checkResponsesFor(d, ProfessorAgent.Role.PartTime)(extraScopeTimeout)
  }

  protected def caseNobodyAccepted(counterpart: NegotiationRole, d: Discipline)(implicit extraScopeTimeout: Timeout) =
    if (!askedForExtraScope)
      askForExtraScope(counterpart) |> searchProfessors |> {
        accMap =>
          askedForExtraScope = true
          SharedNegotiation.transform(NegVars.NewNegAcceptance)(_.map {
            case (k, v) => k -> (v ++ accMap.getOrElse(k, Map()))
          })
      }
    else sys.error(s"no professor could be found for discipline $d")

  private def checkResponsesFor(d: Discipline, counterpart: NegotiationRole)(implicit extraScopeTimeout: Timeout) =
    ifAllResponded _ $ d $ ifAnyAccepted(d, startNegotiatingOver(d), caseNobodyAccepted(counterpart, d))

  private def modifyNewNegAcceptance(b: Boolean, msg: NegotiationEstablishingMessage) = {
    val d = msg.get(Vars.Discipline)
    val par = msg.sender.id -> Some(b)
    SharedNegotiation.transform(NegVars.NewNegAcceptance)( _ $${ _ <<= (d, _.get + par) }  )
  }

  private def ifAllResponded(d: Discipline, f: => Unit) = {
    val accMap = SharedNegotiation(NegVars.NewNegAcceptance)
    if(accMap(d).forall(_._2.nonEmpty)) {
      accMap -= d
      f
    }
  }


  private def ifAnyAccepted(d: Discipline, f: => Unit, fElse: => Unit) =
    if(SharedNegotiation(NegVars.NewNegAcceptance)(d).exists(_._2.getOrElse(false))) f else fElse
}