package feh.tec.agents.schedule

import akka.actor.ActorLogging
import feh.tec.agents.comm.negotiations.Establishing.itHasValues
import feh.tec.agents.comm.negotiations.Issues
import feh.tec.agents.schedule.Messages._
import feh.tec.agents.util.OneToOneNegotiation
import feh.util._
import feh.tec.agents.comm.agent.{NegotiationReactionBuilder, Negotiating}
import feh.tec.agents.comm._
import feh.tec.agents.comm.negotiations.Establishing.{NegotiationProposition, NegotiationAcceptance}
import scala.collection.mutable
import CommonAgentDefs._

class ProfessorAgent( val id: NegotiatingAgentId
                    , val reportTo: SystemAgentRef
                    , val canTeach: Discipline => Boolean
                      )
  extends NegotiatingAgent
  with NegotiationReactionBuilder
  with CommonAgentDefs
  with ProfessorAgentNegotiationPropositionsHandling
  with ProfessorAgentNegotiatingWithGroup
  with ProfessorAgentNegotiatingForClassRoom
  with ActorLogging
  with AgentsTime
{

  def messageReceived: PartialFunction[Message, Unit] = handleNegotiationPropositions orElse handleMessageFromGroups

  def classesAssessor: ClassesBasicPreferencesAssessor[Time] = // todo
    new ClassesBasicPreferencesAssessor[Time]{
      def assess(discipline: Discipline, length: Int, onDay: DayOfWeek, at: Time): InUnitInterval ={
        val endTime = tDescr.fromMinutes(tDescr.toMinutes(at) + length)
        if(tDescr.ending > endTime && timetable.busyAt(onDay, at, endTime)) InUnitInterval(1)
        else InUnitInterval(0)
      }


      def basedOn(p: Param[_]*): DecideInterface = ???
    }

  def assessedThreshold(neg: Negotiation): Float = 0.7f // todo

  protected def negotiationWithId(withAg: NegotiatingAgentRef) = NegotiationId(this.id.name + " -- " + withAg.id.name)

  def start(): Unit = {}
  def stop(): Unit = ???


}

object ProfessorAgent{
  sealed trait Role extends NegotiationRole
  object Role{
    lazy val FullTime = new NegotiationRole("Professor: full-time") with Role
    lazy val PartTime = new NegotiationRole("Professor: part-time") with Role
  }

  def creator( role: ProfessorAgent.Role.type => ProfessorAgent.Role
             , reportTo: SystemAgentRef
             , canTeach: Set[Discipline]
             ) =
    new NegotiatingAgentCreator(role(Role), scala.reflect.classTag[ProfessorAgent],
      id => _ => new ProfessorAgent(id, reportTo, canTeach)
    )
}

trait ProfessorAgentNegotiatingForClassRoom{
  agent: NegotiatingAgent with NegotiationReactionBuilder with CommonAgentDefs =>

  protected def startSearchingForClassRoom(groupNeg: Negotiation): Unit = ???
}

trait ProfessorAgentNegotiatingWithGroup{
  agent: NegotiatingAgent with NegotiationReactionBuilder with CommonAgentDefs with ActorLogging =>

  def assessedThreshold(neg: Negotiation): Float

  def classesAssessor: ClassesBasicPreferencesAssessor[Time]

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
  
  protected def counterProposalOrRejection(prop: ClassesProposal[_], neg: Negotiation): ClassesProposalMessage = {
    ???
  }

  def handleNegotiation: PartialFunction[Message, Unit] = {
    case prop: ClassesProposal[_] =>
      val neg = negotiation(prop.negotiation)
      val a = classesAssessor.assess(discipline(neg), prop.length, prop.day, prop.time.asInstanceOf[Time])

      val resp = if(a > assessedThreshold(neg)) {
                                                  neg.set(Issues.Vars.Issue(Vars.Day))       (prop.day)
                                                  neg.set(Issues.Vars.Issue(Vars.Time[Time]))(prop.time.asInstanceOf[Time])
                                                  neg.set(Issues.Vars.Issue(Vars.Length))    (prop.length)
                                                  startSearchingForClassRoom(neg)
                                                  ClassesAcceptance(neg.id, prop.uuid)
                                                }
                 else                           counterProposalOrRejection(prop, neg)

  }

  private def respondWithDisciplinePriorities() = negotiationsByDiscipline foreach {
    case (discipline, negotiations) =>
      val negIds = negotiations.map(_.id).toSeq
      val counterpartsCounts = counterpartsFoundByTheCounterpart.withFilter(negIds contains _._1).map(_._2.get).toSeq
      assert(counterpartsCounts.distinct.size == 1, "received different counterparts counts: " + counterpartsCounts)
      val nProfs = counterpartsCounts.head
      val p = disciplinePriority(negotiations.size, nProfs)

      negotiations foreach {
        neg =>
          neg.set(NegVars.DisciplinePriority)(p)
          counterpart(neg) ! DisciplinePriorityEstablished(neg.id, p)
          neg.set(NegotiationVar.State)(NegotiationState.Negotiating)
      }

  }

  private def ifAllCounterpartsFoundReceived(f: => Unit) = if (counterpartsFoundByTheCounterpart.values forall (_.isDefined)) f
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
//      val propOrRecall = getFromMsg(msg, Vars.PropOrRecall)

//      if (propOrRecall == Vars.New)
//      else recallRequested(msg)
      msg.sender ! (if (canTeach(disc)) startNegotiationWith(msg.sender, disc) else negotiationRejection(disc))
  }

  /** creates a negotiation and guards it */
  def startNegotiationWith(ag: NegotiatingAgentRef, disc: Discipline): NegotiationAcceptance = {
    add _ $ mkNegotiationWith(ag, disc)
    negotiationAcceptance(disc)
  }
}