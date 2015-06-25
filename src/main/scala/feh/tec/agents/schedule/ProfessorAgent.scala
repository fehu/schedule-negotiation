package feh.tec.agents.schedule

import akka.actor.ActorLogging
import feh.tec.agents.comm.negotiations.Establishing.itHasValues
import feh.tec.agents.comm.negotiations.Issues
import feh.tec.agents.schedule.Messages._
import feh.tec.agents.util.{OneToOneNegotiationId, OneToOneNegotiation}
import feh.util._
import feh.tec.agents.comm.agent.{NegotiationReactionBuilder, Negotiating}
import feh.tec.agents.comm._
import feh.tec.agents.comm.negotiations.Establishing.{NegotiationProposition, NegotiationAcceptance}
import scala.collection.mutable
import CommonAgentDefs._

class ProfessorAgent( val id        : NegotiatingAgentId
                    , val thisIdVal : ProfessorId
                    , val reportTo  : SystemAgentRef
                    , val canTeach  : Discipline => Boolean
                      )
  extends NegotiatingAgent
  with NegotiationReactionBuilder
  with CommonAgentDefs
  with CommonAgentProposal.DefaultAssessor
  with ProfessorAgentNegotiationPropositionsHandling
  with ProfessorAgentNegotiatingWithGroup
  with ProfessorAgentNegotiatingForClassRoom
  with ActorLogging
  with AgentsTime
{

  type ThisId = ProfessorId
  def thisIdVar: NegotiationVar {type T = ThisId} = NegVars.ProfessorId

  def messageReceived: PartialFunction[Message, Unit] = handleNegotiationPropositions orElse handleMessageFromGroups

  def assessedThreshold(neg: Negotiation): Float = 0.7f // todo

  protected def negotiationWithId(withAg: NegotiatingAgentRef) = OneToOneNegotiationId(this.id, withAg.id)

  def start(): Unit = {}
  def stop(): Unit = {
    reportTimetable()
    context.stop(self)
  }


}

object ProfessorAgent{
  sealed trait Role extends NegotiationRole
  object Role{
    lazy val FullTime = new NegotiationRole("Professor-full-time") with Role
    lazy val PartTime = new NegotiationRole("Professor-part-time") with Role
  }

  def creator( role       : ProfessorAgent.Role.type => ProfessorAgent.Role
             , professorId: ProfessorId
             , reportTo   : SystemAgentRef
             , canTeach   : Set[Discipline]
             ) =
    new NegotiatingAgentCreator(role(Role), scala.reflect.classTag[ProfessorAgent],
      id => _ => new ProfessorAgent(id, professorId, reportTo, canTeach)
    )
}

trait ProfessorAgentNegotiatingForClassRoom{
  agent: NegotiatingAgent with NegotiationReactionBuilder with CommonAgentDefs =>

  protected def startSearchingForClassRoom(groupNeg: Negotiation): Unit = {} //todo ???
}

trait ProfessorAgentNegotiatingWithGroup extends CommonAgentProposalAssessment{
  agent: NegotiatingAgent with NegotiationReactionBuilder with CommonAgentDefs with ActorLogging =>

  protected def startSearchingForClassRoom(groupNeg: Negotiation)

  def handleMessageFromGroups = handleNegotiationStart orElse handleNegotiation



  // Starting

  def negotiationsExceptShared = negotiations - SharedNegotiation.id

  protected val counterpartsFoundByTheCounterpart = mutable.Map(negotiationsExceptShared.keys.toSeq.zipMap(_ => Option.empty[Int]): _*)

  protected def negotiationsByDiscipline = negotiationsExceptShared.values.groupBy(_(NegVars.Discipline))

  def disciplinePriority(nGroups: Int, nProfs: Int): Float = nGroups.toFloat / nProfs

  def handleNegotiationStart: PartialFunction[Message, Unit] = {
    case Messages.CounterpartsFound(negId, profCount, _) =>
      counterpartsFoundByTheCounterpart += negId -> Some(profCount)
      ifAllCounterpartsFoundReceived _ $ respondWithDisciplinePriorities()
  }


  // Main

  def handleNegotiation: PartialFunction[Message, Unit] = {
    case prop: ClassesProposalMessage[Time] =>
      val neg = negotiation(prop.negotiation)
      val resp = handleClassesProposalMessage(prop, neg) match {
        case Left(counter) => counter
        case Right(accept) =>
          startSearchingForClassRoom(neg)
          accept
      }
      prop.sender ! resp
    case (msg: ClassesMessage) suchThat NotAwaitingResponse() => // do nothing
//    case (acc: ClassesAcceptance[Time]) => // todo
  }

  private def respondWithDisciplinePriorities() = negotiationsByDiscipline foreach {
    case (discipline, negotiations) =>
      val negIds = negotiations.map(_.id).toSeq
      val counterpartsCounts = counterpartsFoundByTheCounterpart.withFilter(negIds contains _._1).map(_._2.get).toSeq
//      log.debug("Professor: counterpartsCounts = " + counterpartsCounts)
      assert(counterpartsCounts.distinct.size <= 1, "received different counterparts counts: " + counterpartsCounts)
      counterpartsCounts.headOption foreach{
        nProfs =>
          val p = disciplinePriority(negotiations.size, nProfs)

          negotiations foreach {
            neg =>
              neg.set(NegVars.DisciplinePriority)(p)
              counterpart(neg) ! DisciplinePriorityEstablished(neg.id, p)
              neg.set(NegotiationVar.State)(NegotiationState.Negotiating)
          }
      }

  }

  private def ifAllCounterpartsFoundReceived(f: => Unit) =
    if (counterpartsFoundByTheCounterpart.values |> (vs => vs.nonEmpty && vs.forall(_.isDefined))) f
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
      val id = getFromMsg(msg, Vars.EntityId).asInstanceOf[GroupId]
//      val propOrRecall = getFromMsg(msg, Vars.PropOrRecall)

//      if (propOrRecall == Vars.New)
//      else recallRequested(msg)
      msg.sender ! (if (canTeach(disc)) startNegotiationWith(msg.sender, disc, id) else negotiationRejection(disc))
  }

  /** creates a negotiation and guards it */
  def startNegotiationWith(ag: NegotiatingAgentRef, disc: Discipline, id: GroupId): NegotiationAcceptance = {
    add _ $ mkNegotiationWith(ag, disc, NegVars.GroupId, id)
    negotiationAcceptance(disc, thisIdVal)
  }
}