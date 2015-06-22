package feh.tec.agents.schedule

import java.util.UUID

import akka.actor.ActorLogging
import akka.util.Timeout
import feh.tec.agents.comm.NegotiationVar.Scope
import feh.tec.agents.comm._
import feh.tec.agents.comm.agent.{Negotiating, NegotiationReactionBuilder}
import feh.tec.agents.comm.negotiations.Establishing._
import feh.tec.agents.comm.negotiations.Proposals.Vars.CurrentProposal
import feh.tec.agents.comm.negotiations._
import feh.tec.agents.schedule.CommonAgentDefs._
import feh.tec.agents.schedule.Messages._
import feh.tec.agents.schedule.io.StudentsSelection
import feh.tec.agents.util.OneToOneNegotiationId
import feh.util._

import scala.collection.mutable
import scala.concurrent.Await

class GroupAgent( val id                : NegotiatingAgentId
                , val coordinator       : AgentRef
                , val reportTo          : SystemAgentRef
                , val discipline        : Discipline
                , val initialStudents   : Set[NegotiatingAgentRef]
                , val timeouts          : CommonAgentDefs.Timeouts
                , val schedulePolicy    : SchedulePolicy
                  )
  extends NegotiatingAgent
  with NegotiationReactionBuilder
  with CommonAgentDefs
  with GroupAgentNegotiationPropositionsHandling
  with GroupAgentNegotiating
  with GroupAgentProposals
  with GroupAgentStudentsHandling
  with ActorLogging
  with AgentsTime
{
  val classesDecider = new ClassesBasicPreferencesDeciderImplementations[Time]{
    def basedOn(p: Param[_]*): AbstractDecideInterface =
      new DecideRandom(p, lengthDiscr = 60, getParam(disciplineParam, p).value.classes/*todo: labs*/, timetable, log)
  }


  def messageReceived: PartialFunction[Message, Unit] =
    handleNewNegotiations orElse handleMessage orElse handleStudents

  def askForExtraScope(role: NegotiationRole)(implicit timeout: Timeout): Set[NegotiatingAgentRef] = {
//    log.debug("askForExtraScope")
    val res = Await.result(
      (coordinator ? CoordinatorAgent.ExtraScopeRequest(role)).mapTo[Set[NegotiatingAgentRef]],
      timeout.duration*1.1
    )
//    log.debug("extra scope: " + res)
    res
  }

  def extraScopeTimeout = timeouts.extraScopeTimeout

  protected def negotiationWithId(withAg: NegotiatingAgentRef) = OneToOneNegotiationId(this.id, withAg.id)

  def start(): Unit = {

  }

  def stop(): Unit = {
    reportTimetable()
    context.stop(self)
  }
}

object GroupAgent{
  type MinutesPerWeek = Discipline.MinutesPerWeek


  object Role extends NegotiationRole("Group")

  def creator( reportTo           : SystemAgentRef
             , discipline         : Discipline
             , timeouts           : Timeouts
             , schedulePolicy     : SchedulePolicy
             , initialStudents    : Set[NegotiatingAgentRef] = Set()) =
    new NegotiatingAgentCreator(Role, scala.reflect.classTag[GroupAgent],
      id => {
        case Some(coordinator) =>
          new GroupAgent(id, coordinator, reportTo, discipline, initialStudents, timeouts, schedulePolicy)
      }
    )

  case class AddStudent(discipline: Discipline, studentId: StudentId, studentRef: NegotiatingAgentRef, nTry: Int = 0)
                       (implicit val sender: AgentRef) extends UUIDed with Message
  {
    val tpe = "Add student"
    val asString = studentId.toString + ", try " + nTry
  }

  case class RmStudent(studentId: StudentId)(implicit val sender: AgentRef) extends UUIDed with Message {
    val tpe = "Remove student"
    val asString = studentId.toString
  }

  case class GroupIsFull(discipline: Discipline, studentId: StudentId, studentRef: NegotiatingAgentRef, nTry: Int)
                        (implicit val sender: AgentRef) extends UUIDed with Message
  {
    val tpe = "I am full. Returning the student"
    val asString = studentId.toString + ", try " + nTry
  }

  case class StartSearchingProfessors(implicit val sender: AgentRef) extends UUIDed with Message{
    val tpe = "Start Searching Professors"
    val asString = ""
  }

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

  def handleMessage = handleNegotiationStart orElse handleNegotiation

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
      neg.set(CurrentProposal)(proposal)
      awaitResponseFor(proposal)
  }

  def handleNegotiation: PartialFunction[Message, Unit] = {
    case (msg: ClassesAcceptance[_]) suchThat AwaitingResponse() =>
      /*todo: use Confirm message*/
      log.debug("Acceptance")
      val neg = negotiation(msg.negotiation)
      val prop = neg(CurrentProposal).ensuring(_.uuid == msg.respondingTo)
      def get[T] = getFromMsg(prop.asInstanceOf[ClassesProposalMessage], _: Var[T])
      val start = get(Vars.Time[Time])
      val end = tDescr.fromMinutes(tDescr.toMinutes(start) + get(Vars.Length))
      val clazz = ClassId(neg(NegVars.Discipline).code)
      timetable.putClass(get(Vars.Day), start, end, clazz)
      log.debug("putClass")
      noResponsesExpected(msg.negotiation)
    case msg => //sys.error("todo: handle " + msg)
  }

}

trait GroupAgentNegotiationPropositionsHandling extends Negotiating.DynamicNegotiations with ActorLogging {
  agent: NegotiatingAgent with NegotiationReactionBuilder with CommonAgentDefs =>

  val discipline: Discipline

  def startNegotiatingOver(d: Discipline)

  def askForExtraScope(role: NegotiationRole)(implicit timeout: Timeout): Set[NegotiatingAgentRef]

  def extraScopeTimeout: Timeout

  def startSearchingProfessors() = SharedNegotiation.set(NegVars.NewNegAcceptance)(searchProfessors())

  def professorRefs = SharedNegotiation(Scope).filter(_.id.role.isInstanceOf[ProfessorAgent.Role])

  def searchProfessors(profRefs: Set[NegotiatingAgentRef] = professorRefs) = profRefs
    .map{
          profRef =>
            profRef ! negotiationProposition(Vars.Discipline -> discipline)
            profRef.id -> Option.empty[Boolean]
        }
    .toMap

  private var askedForExtraScope = false

  def handleNewNegotiations: PartialFunction[Message, Unit] = {
    case GroupAgent.StartSearchingProfessors() => startSearchingProfessors()
    case msg: NegotiationProposition => sys.error("todo: recall")   // todo: recall

    case (msg: NegotiationAcceptance) /*suchThat AwaitingResponse()*/ & WithDiscipline(`discipline`) =>
      add _ $ mkNegotiationWith(msg.sender, discipline)
//      log.debug("mkNegotiationWith " + sender + " over " + discipline)
      modifyNewNegAcceptance(true, msg)
      checkResponsesForPartTime()

    case (msg: NegotiationRejection) /*& AwaitingResponse()*/ & WithDiscipline(`discipline`) =>
      modifyNewNegAcceptance(false, msg)
      checkResponsesForPartTime()
  }

  protected def caseNobodyAccepted(counterpart: NegotiationRole)(implicit extraScopeTimeout: Timeout) =
    if (!askedForExtraScope)
      askForExtraScope(counterpart) match {
        case set if set.isEmpty => noCounterpartFound()
        case set => searchProfessors(set) |> {
          accMap =>
            askedForExtraScope = true
            SharedNegotiation.transform(NegVars.NewNegAcceptance){_ ++ accMap} // todo: check it
        }
      }
    else noCounterpartFound()

  private def noCounterpartFound() = {
    reportTo ! Messages.NoCounterpartFound(discipline)
//    sys.error(s"no professor could be found for discipline $d")
  }

  private def checkResponsesForPartTime() = checkResponsesFor(ProfessorAgent.Role.PartTime)(extraScopeTimeout)

  private def checkResponsesFor(counterpart: NegotiationRole)(implicit extraScopeTimeout: Timeout) ={
    val acc = SharedNegotiation(NegVars.NewNegAcceptance)
    ifAllResponded _ $ ifAnyAccepted(acc, startNegotiatingOver(discipline), caseNobodyAccepted(counterpart))
  }


  private def modifyNewNegAcceptance(b: Boolean, msg: NegotiationEstablishingMessage) =
    SharedNegotiation.transform(NegVars.NewNegAcceptance)(_ + (msg.sender.id -> Some(b)))

  private def ifAllResponded(f: => Unit) = {
    val accMap = SharedNegotiation(NegVars.NewNegAcceptance)
    if(accMap.nonEmpty && accMap.forall(_._2.nonEmpty)) {
      SharedNegotiation.set(NegVars.NewNegAcceptance)(Map())
      f
    }
  }


  private def ifAnyAccepted(acceptance: Map[NegotiatingAgentId, Option[Boolean]],
                            f: => Unit,
                            fElse: => Unit) =
  {
    if(acceptance.exists(_._2.getOrElse(false))) f else fElse
  }

}

trait GroupAgentStudentsHandling{
  agent: NegotiatingAgent with NegotiationReactionBuilder with CommonAgentDefs =>

  def schedulePolicy: SchedulePolicy
  def discipline    : Discipline

  protected val students = mutable.HashMap.empty[StudentId, NegotiatingAgentRef]

  def handleStudents: PartialFunction[Message, Unit] = {
    case msg: GroupAgent.AddStudent if students.size >= schedulePolicy.maxStudentsInGroup =>
      msg.sender ! GroupAgent.GroupIsFull(discipline, msg.studentId, msg.studentRef, msg.nTry)
    case GroupAgent.AddStudent(d, studentId, studentRef, _) =>
      assert(discipline == d)
      students += studentId -> studentRef
      studentRef ! StudentAgent.AddedToGroup(discipline, ref)
    case GroupAgent.RmStudent(studentId) =>
      val Some(sRef) = students.remove(studentId) // todo: handle error
      sRef ! StudentAgent.RemovedFromGroup(discipline, ref)
  }
}