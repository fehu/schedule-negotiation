package feh.tec.agents.schedule

import akka.actor.ActorLogging
import akka.util.Timeout
import feh.tec.agents.comm.NegotiationVar.Scope
import feh.tec.agents.comm._
import feh.tec.agents.comm.agent.{NegotiationReactionBuilder, Negotiating}
import feh.tec.agents.comm.negotiations._
import Establishing._
import feh.tec.agents.schedule.Messages.{StartingNegotiation, ClassesProposal, CounterpartsFound}
import scala.collection.mutable
import feh.util._
import CommonAgentDefs._

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
  with ActorLogging
  with AgentsTime
{
  val classesDecider = new ClassesBasicPreferencesDeciderImplementations[Time]{
    def basedOn(p: Param[_]*): AbstractDecideInterface =
      new DecideRandom(p, lengthDiscr = 90, toAttend(getParam(disciplineParam, p).value), timetable, log)
  }


  def messageReceived: PartialFunction[Message, Unit] = handleNewNegotiations orElse handleMessage

  def askForExtraScope(role: NegotiationRole)(implicit timeout: Timeout): Set[NegotiatingAgentRef] = {
    log.debug("askForExtraScope")
    val res = Await.result(
      (coordinator ? CoordinatorAgent.ExtraScopeRequest(role)).mapTo[Set[NegotiatingAgentRef]],
      timeout.duration*1.1
    )
    log.debug("extra scope: " + res)
    res
  }

  def extraScopeTimeout = timeouts.extraScopeTimeout

  protected def negotiationWithId(withAg: NegotiatingAgentRef) = NegotiationId(withAg.id.name + " -- " + this.id.name)

  def start(): Unit = {
    startSearchingProfessors()
  }

  def stop(): Unit = ???
}

object GroupAgent{
  type MinutesPerWeek = Int

  type DisciplinesToAttend = Map[Discipline, MinutesPerWeek]

  object Role extends NegotiationRole("Group")

  def creator(reportTo: SystemAgentRef, toAttend: DisciplinesToAttend, timeouts: Timeouts) =
    new NegotiatingAgentCreator(Role, scala.reflect.classTag[GroupAgent],
      id => {
        case Some(coordinator) => new GroupAgent(id, coordinator, reportTo, toAttend, timeouts)
      }
    )
}

/** Creating new proposals and evaluating already existing
 *
 */
trait GroupAgentProposals{
  self: NegotiatingAgent with CommonAgentDefs =>

  val classesDecider: ClassesBasicPreferencesDecider[Time]

  def nextProposalFor(neg: Negotiation): ClassesProposal[Time] = {
    import classesDecider._

    val d = basedOn(disciplineParam -> neg(NegVars.Discipline))

    val (day, time, len) = d decide (whatDay_?, whatTime_?, howLong_?)

    ClassesProposal(neg.id, getDecision(day), getDecision(time), getDecision(len))
  }
}

trait GroupAgentNegotiating{
  agent: NegotiatingAgent with NegotiationReactionBuilder with CommonAgentDefs with ActorLogging =>

  def handleMessage = handleNegotiationStart // orElse handleNegotiation

  def nextProposalFor(neg: Negotiation): Proposals.Proposal

  def negotiationsOver(d: Discipline) = negotiations.map(_._2).filter(_.get(NegVars.Discipline) contains d)

  def startNegotiatingOver(d: Discipline): Unit = {
    val counterparts = negotiatingWithOver(d)
    val n = counterparts.size

    counterparts foreach {
      case (neg, counterpart) =>
        reportTo    ! StartingNegotiation(d, counterpart)
        counterpart ! CounterpartsFound(neg.id, n)
    }
  }

  protected def negotiatingWithOver(d: Discipline) = negotiationsOver(d).flatMap{
    neg => counterpartOpt(neg).map(neg -> _)
  }

  def handleNegotiationStart: PartialFunction[Message, Unit] = {  // todo: maybe wait until all discipline priorities established?
    case Messages.DisciplinePriorityEstablished(negId, priority, _) =>
      val neg = negotiation(negId)
      neg.set(NegVars.DisciplinePriority)(priority)
      neg.set(NegotiationVar.State)(NegotiationState.Negotiating)
      val proposal = nextProposalFor(neg)
      counterpart(neg) ! proposal
  }

//  def handleNegotiation: PartialFunction[Message, Unit] = {
//    case msg => sys.error("todo: handle " + msg)
//  }

}

trait GroupAgentNegotiationPropositionsHandling extends Negotiating.DynamicNegotiations with ActorLogging {
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

    case (msg: NegotiationAcceptance) /*suchThat AwaitingResponse()*/ & WithDiscipline(d) =>
      add _ $ mkNegotiationWith(msg.sender, d)
      modifyNewNegAcceptance(true, msg)
      checkResponsesFor(d, ProfessorAgent.Role.PartTime)(extraScopeTimeout)

    case (msg: NegotiationRejection) /*& AwaitingResponse()*/ & WithDiscipline(d) =>
      modifyNewNegAcceptance(false, msg)
      checkResponsesFor(d, ProfessorAgent.Role.PartTime)(extraScopeTimeout)
  }

  protected def caseNobodyAccepted(counterpart: NegotiationRole, d: Discipline)(implicit extraScopeTimeout: Timeout) =
    if (!askedForExtraScope)
      askForExtraScope(counterpart) match {
        case set if set.isEmpty => noCounterpartFound(d)
        case set => searchProfessors(set) |> {
          accMap =>
            askedForExtraScope = true
            SharedNegotiation.transform(NegVars.NewNegAcceptance)(_.map {
              case (k, v) => k -> (v ++ accMap.getOrElse(k, Map()))
            })
        }
      }
    else noCounterpartFound(d)

  private def noCounterpartFound(d: Discipline) = {
    reportTo ! Messages.NoCounterpartFound(d)
//    sys.error(s"no professor could be found for discipline $d")
  }

  private def checkResponsesFor(d: Discipline, counterpart: NegotiationRole)(implicit extraScopeTimeout: Timeout) ={
    val acc = SharedNegotiation(NegVars.NewNegAcceptance).toMap
    ifAllResponded _ $ d $ ifAnyAccepted(d, acc, startNegotiatingOver(d), caseNobodyAccepted(counterpart, d))
  }


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


  private def ifAnyAccepted(d: Discipline,
                            acceptance: Map[Discipline, Map[NegotiatingAgentId, Option[Boolean]]],
                            f: => Unit,
                            fElse: => Unit) =
  {
    if(acceptance(d).exists(_._2.getOrElse(false))) f else fElse
  }

}