package feh.tec.agents.schedule

import akka.actor.ActorLogging
import feh.tec.agents.comm.NegotiationVar.Scope
import feh.tec.agents.comm._
import feh.tec.agents.comm.agent.{Negotiating, NegotiationReactionBuilder}
import feh.tec.agents.comm.negotiations.Establishing._
import feh.tec.agents.comm.negotiations.Proposals.Vars.CurrentProposal
import feh.tec.agents.schedule.CommonAgentDefs._
import feh.tec.agents.schedule.CoordinatorAgent.ExtraScopeResponse
import feh.tec.agents.schedule.Discipline._
import feh.tec.agents.schedule.Messages._
import feh.tec.agents.util.OneToOneNegotiationId
import feh.util._

import scala.collection.mutable

class GroupAgent( val id                : NegotiatingAgentId
                , val thisIdVal         : GroupId
                , val coordinator       : AgentRef
                , val reportTo          : SystemAgentRef
                , val discipline        : Discipline
                , val initialStudents   : Set[NegotiatingAgentRef]
                , val timeouts          : CommonAgentDefs.Timeouts
                , val schedulePolicy    : SchedulePolicy
                  )
  extends NegotiatingAgent
  with NegotiationReactionBuilder
  with CommonUtilityDrivenDefs
  with RandomProposalChooser.Group
  with GroupAgentNegotiationPropositionsHandling
  with GroupAgentNegotiating
  with GroupAgentStudentsHandling
  with ActorLogging
{

  type ThisId = GroupId
  def thisIdVar = NegVars.GroupId

  type NegotiationTime = AnyRef
  def negotiationTime = null


  def preference(time: NegotiationTime, gh: GoalHolder, proposal: ProposalType) = 1d // todo

  // todo: labs
  def goalAchievement(gh: GoalHolder) = {
    val assignedClassesDurations = ImmutableTimetable.filterEmpty(gh.asMap)
                                   .mapValues(classesDuration)
                                   .values.flatten
                                   .groupBy(_._1)
                                   .mapValues(_.map(_._2).sum)

    val durC = discipline.classes
    val durL = discipline.labs // todo: labs

    val acd = assignedClassesDurations
               .ensuring(_.forall(_._1.discipline == discipline))
               .map(_._2)
               .sum

    val aC = acd.toDouble / durC

    if (aC == 1 && acd != durC) reportTo ! Report.Debug("goalAchievement", s"discipline = $discipline, aC = $aC, acd = $acd")

    InUnitInterval(if (aC > 1) 0 else aC) // aC max 0
  }

  def assessedThreshold(neg: Negotiation) = 0.7 // todo


  // todo: distinct classes and labs
  private def classesDuration(mp: Map[_, Class[Time]]): Map[Class[Time], MinutesPerWeek] = {
    val td = implicitly[TimeDescriptor[Time]]
    mp.values.groupBy(identity).mapValues(_.size * td.step) // ??
  }

  def messageReceived: PartialFunction[Message, Unit] =
    handleNewNegotiations orElse handleMessage orElse handleStudents

  protected def askForExtraScope(role: NegotiationRole) = coordinator ! CoordinatorAgent.ExtraScopeRequest(role)

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
             , groupId            : GroupId
             , discipline         : Discipline
             , timeouts           : Timeouts
             , schedulePolicy     : SchedulePolicy
             , initialStudents    : Set[NegotiatingAgentRef] = Set()) =
    new NegotiatingAgentCreator(Role, scala.reflect.classTag[GroupAgent],
      id => {
        case Some(coordinator) =>
          new GroupAgent(id, groupId, coordinator, reportTo, discipline, initialStudents, timeouts, schedulePolicy)
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

trait GroupAgentNegotiating{
  agent: NegotiatingAgent
    with NegotiationReactionBuilder
    with CommonAgentDefs
    with UtilityDriven
    with ActorLogging =>

  def handleMessage = handleNegotiationStart orElse handleNegotiation

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
//      log.debug("Acceptance")
      val neg = negotiation(msg.negotiation)
      val prop = neg(CurrentProposal).asInstanceOf[ClassesProposalMessage[Time]]

//      val reject_? = isBusyAt(discipline(neg), prop.length, prop.day, prop.time).getOrElse(true)

//      if(reject_?) ???
//      else todo: ask students first

      putClass(prop) match {
        case Left(_) =>
          reportTo ! Report.Debug("handleNegotiation:putClass", "counter proposal")
          val prop  = nextProposalFor(neg).asInstanceOf[ClassesProposal[Time]]
          val cprop = ClassesCounterProposal(prop.negotiation, msg.uuid, prop.day, prop.time, prop.length, prop.extra)
          counterpart(neg) ! cprop
          neg.set(CurrentProposal)(cprop)
          awaitResponseFor(cprop)
        case _ =>
          reportTo ! Report.Debug("handleNegotiation:putClass", "ok")
          noResponsesExpected(msg.negotiation)
      }
    case (msg: ClassesCounterProposal[Time]) suchThat AwaitingResponse() & WithNegotiation(neg) =>
      val resp = handleClassesProposalMessage(msg, neg)
      counterpart(neg) ! resp.merge
    case (msg: ClassesMessage) suchThat NotAwaitingResponse() => // do nothing
  }

}

trait GroupAgentNegotiationPropositionsHandling extends Negotiating.DynamicNegotiations with ActorLogging {
  agent: NegotiatingAgent with NegotiationReactionBuilder with CommonAgentDefs =>

  val discipline: Discipline

  def startNegotiatingOver(d: Discipline)

  protected def askForExtraScope(role: NegotiationRole)


  def startSearchingProfessors() = SharedNegotiation.set(NegVars.NewNegAcceptance)(searchProfessors())

  def professorRefs = SharedNegotiation(Scope).filter(_.id.role.isInstanceOf[ProfessorAgent.Role])

  def searchProfessors(profRefs: Set[NegotiatingAgentRef] = professorRefs) = profRefs
    .map{
          profRef =>
            profRef ! negotiationProposition(Vars.Discipline -> discipline, Vars.EntityId -> thisIdVal)
            profRef.id -> Option.empty[Boolean]
        }
    .toMap

  private var askedForExtraScope = false

  def handleNewNegotiations: PartialFunction[Message, Unit] = {
    case GroupAgent.StartSearchingProfessors() => startSearchingProfessors()
    case msg: NegotiationProposition => sys.error("todo: recall")   // todo: recall

    case (msg: NegotiationAcceptance) /*suchThat AwaitingResponse()*/ & WithDiscipline(`discipline`) =>
      val id = getFromMsg(msg, Vars.EntityId).asInstanceOf[ProfessorId]
      add _ $ mkNegotiationWith(msg.sender, discipline, NegVars.ProfessorId, id)
      log.debug("mkNegotiationWith " + msg.sender.id.name + " over " + discipline)
      modifyNewNegAcceptance(true, msg)
      checkResponses()

    case (msg: NegotiationRejection) /*& AwaitingResponse()*/ & WithDiscipline(`discipline`) =>
      modifyNewNegAcceptance(false, msg)
      checkResponses()

    case msg: ExtraScopeResponse => extraScopeRecieved(msg.scope)
  }

  protected def extraScopeRecieved(scope: Set[NegotiatingAgentRef]) = scope match {
    case set if set.isEmpty => noCounterpartFound()
    case set => searchProfessors(set) |> {
      accMap =>
        askedForExtraScope = true
        SharedNegotiation.transform(NegVars.NewNegAcceptance){_ ++ accMap} // todo: check it
    }
  }

  protected def caseNobodyAccepted(counterpart: NegotiationRole) =
    if (!askedForExtraScope) askForExtraScope(counterpart)
    else noCounterpartFound()

  private def noCounterpartFound() = {
    reportTo ! Messages.NoCounterpartFound(discipline)
//    sys.error(s"no professor could be found for discipline $d")
  }

  private def checkResponses() = checkResponsesFor(ProfessorAgent.Role.PartTime) // ask for PartTime extra scope

  private def checkResponsesFor(counterpart: NegotiationRole) ={
    val acc = SharedNegotiation(NegVars.NewNegAcceptance)
    ifAllResponded _ $ ifAnyAccepted(acc, startNegotiatingOver(discipline), caseNobodyAccepted(counterpart))
  }


  private def modifyNewNegAcceptance(b: Boolean, msg: NegotiationEstablishingMessage) =
    SharedNegotiation.transform(NegVars.NewNegAcceptance)(_ + (msg.sender.id -> Some(b)))

  private def ifAllResponded(f: => Unit) = {
    val accMap = SharedNegotiation(NegVars.NewNegAcceptance)
    if(accMap.nonEmpty && accMap.forall(_._2.nonEmpty)) {
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