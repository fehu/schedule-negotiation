package feh.tec.agents.schedule

import akka.actor.ActorLogging
import akka.util.Timeout
import feh.tec.agents.comm.Message.HasValues
import feh.tec.agents.comm.Negotiation.VarUpdated
import feh.tec.agents.comm.Report.StateChanged
import feh.tec.agents.comm._
import feh.tec.agents.comm.agent.Negotiating.DynamicNegotiations
import feh.tec.agents.comm.agent.NegotiationReactionBuilder
import feh.tec.agents.comm.negotiations.Establishing.{NegotiationAcceptance, NegotiationEstablishingMessage, NegotiationProposition, NegotiationRejection}
import feh.tec.agents.comm.negotiations.{Issues, Var}
import feh.tec.agents.schedule.CommonAgentDefs._
import feh.tec.agents.schedule.Messages._
import feh.tec.agents.util.OneToOneNegotiation
import feh.util.InUnitInterval

trait CommonAgentDefs extends AgentsTime{
  agent: NegotiatingAgent with DynamicNegotiations =>

  type Time

  /** a negotiation that is easily accessed from others negotiations */
  lazy val SharedNegotiation = new SharedNegotiation(varUpdatedNotification) {
    defineVar(NegVars.NewNegAcceptance)
  }

  add(SharedNegotiation)
  
  override lazy val Reporting = new ReportingNegotiationsConfig(stateChanged = false)

  protected def varUpdatedNotification(upd: VarUpdated[_ <: NegotiationVar]) =
    if(Reporting.stateChanged) reportTo ! StateChanged(upd)



  protected def negotiationWithId(withAg: NegotiatingAgentRef): NegotiationId

  type ThisId <: EntityId
  def thisIdVar: NegotiationVar{ type T = ThisId }
  def thisIdVal: ThisId

  protected def mkNegotiationWith[Id]( withAg: NegotiatingAgentRef
                                        , disc: Discipline
                                        , thatIdVar: NegotiationVar{ type T = Id }
                                        , thatIdVal: Id): Negotiation =
    new Negotiation(negotiationWithId(withAg), varUpdatedNotification) with ANegotiation[Time] {
      def discipline = disc

      set(NegVars.Discipline)(disc)
      set(OneToOneNegotiation.NegotiatingWith)(withAg)

      set(thisIdVar)(thisIdVal)
      set(thatIdVar)(thatIdVal)
    }

  def isBusyAt(discipline: Discipline, length: Int, onDay: DayOfWeek, at: Time) = {
    val minutes = tDescr.toMinutes(at) + length
    tDescr.fromMinutesOpt(minutes).map{ endTime => timetable.busyAt(onDay, at, endTime)}
  }


  def negotiationProposition(vals: (Var[Any], Any)*) = new NegotiationProposition{
    val values = vals.toMap
    override val sender: NegotiatingAgentRef = implicitly
  }
  
  def negotiationRejection(d: Discipline)(implicit snd: NegotiatingAgentRef) = new NegotiationRejection{
    val values: Map[Var[Any], Any] = Map(Vars.Discipline -> d)
    override val sender = snd
  }

  def negotiationAcceptance(d: Discipline, id: EntityId) = new NegotiationAcceptance {
    val values: Map[Var[Any], Any] = Map(Vars.Discipline -> d, Vars.EntityId -> id)
    override val sender: NegotiatingAgentRef = implicitly
  }

  object Tst{
    def unapply(msg: Message): Option[Boolean] = Some(true)
  }

  object WithDiscipline{
    def unapply(msg: Message): Option[Discipline] = msg match {
      case msg: NegotiationEstablishingMessage =>
        implicitly[HasValues[NegotiationEstablishingMessage]].valueOpt(msg)(Vars.Discipline)
    }
  }

  def getDecision[T](d: AbstractDecider#Decision[T]): T = d.value.right.map(throw _).merge

  def reportTimetable() = reportTo ! TimetableReport(ImmutableTimetable(timetable.asMap))

  def putClass(prop: ClassesProposalMessage[Time]) = {
    val neg = negotiation(prop.negotiation)
    val start = prop.time
    val end = tDescr.fromMinutes(tDescr.toMinutes(start) + prop.length)
    val id = ClassId(neg(NegVars.Discipline).code)
    val groupId = neg(NegVars.GroupId)
    val profId =  neg(NegVars.ProfessorId)
    val classId =  ClassRoomId.Unassigned
    val clazz = Class(discipline(neg), prop.day, start, end, groupId, profId, classId)
    timetable.put(prop.day, start, end, clazz)
  }
}

object CommonAgentDefs{
  case class Timeouts( extraScopeTimeout: Timeout
                       )

  def counterpartOpt(neg: Negotiation) = neg.get(OneToOneNegotiation.NegotiatingWith)
  def counterpart(neg: Negotiation)    = counterpartOpt(neg).get

  def disciplineOpt(neg: Negotiation)  = neg.get(NegVars.Discipline)
  def discipline(neg: Negotiation)     = disciplineOpt(neg).get

}








trait AgentsTime{
  agent: NegotiatingAgent =>

  type Time = feh.tec.agents.schedule.Time

  implicit def tDescr = AgentsTime.tDescr

  val timetable = new MutableTimetable[Class[Time]]
}

object AgentsTime{
  implicit val tDescr = Time.descriptor(8*60, 22*60, 30)
}





/** Creating new proposals
  *
  */

trait CommonAgentProposalsGeneration extends NegotiatingAgent{
  agent: CommonAgentDefs =>

  val classesDecider: ClassesBasicPreferencesDecider[Time]

  def nextProposalFor(neg: Negotiation): ClassesProposal[Time] = {
    import classesDecider._


    val d = basedOn(lengthParam -> neg(NegVars.Discipline).classes)

    val (day, time, len) = d decide (whatDay_?, whatTime_?, howLong_?)

    ClassesProposal(neg.id, getDecision(day), getDecision(time), getDecision(len))
  }

}


/** Evaluating existing proposals
  *
  */

trait CommonAgentProposalAssessment extends NegotiatingAgent{
  agent: CommonAgentDefs with NegotiationReactionBuilder with ActorLogging =>

  def assessedThreshold(neg: Negotiation): Float

  val classesAssessor: ClassesBasicPreferencesAssessor[Time]


  protected def counterProposal(prop: ClassesProposalMessage[_], neg: Negotiation) = {
    import classesAssessor._
    val d = classesAssessor.basedOn(lengthParam -> prop.length)
    val (day, time, len) = d decide (whatDay_?, whatTime_?, howLong_?)
    val cprop = ClassesCounterProposal(neg.id, prop.uuid, getDecision(day), getDecision(time), getDecision(len))
    awaitResponseFor(cprop)
    cprop
  }
  
  protected def acceptance(prop: ClassesProposalMessage[Time], neg: Negotiation) = {
    neg.set(Issues.Vars.Issue(Vars.Day))       (prop.day)
    neg.set(Issues.Vars.Issue(Vars.Time[Time]))(prop.time)
    neg.set(Issues.Vars.Issue(Vars.Length))    (prop.length)
    
    ClassesAcceptance[Time](neg.id, prop.uuid)
  }

  type HandleClassesProposalMessageResult = Either[ClassesCounterProposal[Time], ClassesAcceptance[Time]]

  protected def handleClassesProposalMessage( prop: ClassesProposalMessage[Time]
                                            , neg_ : Negotiation = null): HandleClassesProposalMessageResult = {
      val neg = Option(neg_) getOrElse negotiation(prop.negotiation)
      val a = classesAssessor.assess(discipline(neg), prop.length, prop.day, prop.time)

      //      log.debug("proposal assessed: " + a)

      if(a > assessedThreshold(neg)) {
        log.debug("Acceptance")
        /*todo: use Confirm message*/
        putClass(prop) match {
          case Left(_) =>
            Left(counterProposal(prop, neg))
          case _ =>
            log.debug("putClass")
            Right(acceptance(prop, neg))
        }
      }
      else Left(counterProposal(prop, neg))


  }

}

object CommonAgentProposal{

  protected trait DefaultDeciderImpl {
    agent: CommonAgentDefs with NegotiationReactionBuilder with ActorLogging =>

    class DeciderImpl extends ClassesBasicPreferencesDeciderImplementations[Time]{
      def basedOn(p: Param[_]*): AbstractDecideInterface =
        new DecideRandom(p, lengthDiscr = 60, getParam(lengthParam, p).value /*todo: labs*/, timetable, log)
    }
  }

  trait DefaultDecider extends DefaultDeciderImpl{
    agent: CommonAgentProposalsGeneration with CommonAgentDefs with NegotiationReactionBuilder with ActorLogging =>

    lazy val classesDecider = new DeciderImpl
  }

  trait DefaultAssessor extends DefaultDeciderImpl{
    agent: CommonAgentProposalAssessment with CommonAgentDefs with NegotiationReactionBuilder with ActorLogging =>

    lazy val classesAssessor: ClassesBasicPreferencesAssessor[Time] = // todo
      new DeciderImpl with ClassesBasicPreferencesAssessor[Time]{
        def assess(discipline: Discipline, length: Int, onDay: DayOfWeek, at: Time): InUnitInterval =
          InUnitInterval(if(isBusyAt(discipline, length, onDay, at) contains false) 1 else 0)
      }

  }

}