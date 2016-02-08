package feh.tec.agents.schedule

import feh.tec.agents.comm.Message.HasValues
import feh.tec.agents.comm.Negotiation.VarUpdated
import feh.tec.agents.comm.Report.StateChanged
import feh.tec.agents.comm._
import feh.tec.agents.comm.agent.Negotiating.DynamicNegotiations
import feh.tec.agents.comm.agent.NegotiationReactionBuilder
import feh.tec.agents.comm.negotiations.Establishing.{NegotiationAcceptance, NegotiationEstablishingMessage, NegotiationProposition, NegotiationRejection}
import feh.tec.agents.comm.negotiations.Var
import feh.tec.agents.schedule.CommonAgentDefs._
import feh.tec.agents.schedule.Messages._
import feh.tec.agents.util.OneToOneNegotiation

trait CommonAgentDefs extends AgentsTime with PutClassesInterface{
  agent: NegotiatingAgent with DynamicNegotiations =>

  type Time

  implicit def timeDescriptor: TimeDescriptor[Time]

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
    val minutes = timeDescriptor.toMinutes(at) + length
    timeDescriptor.fromMinutesOpt(minutes).map{ endTime => timetable.busyAt(onDay, at, endTime)}
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


  def reportTimetable() = reportTo ! TimetableReport(ImmutableTimetable(timetable.asMap))

  def putClassIn(prop: ClassesProposalMessage[Time], tt: MutableTimetable[Class[Time]])=
  {
    val neg = negotiation(prop.negotiation)
    val start = prop.time
    val endT = timeDescriptor.toMinutes(start) + prop.length
    timeDescriptor.fromMinutesOpt(endT).map{
                                     end =>
                                       val id = ClassId(neg(NegVars.Discipline).code)
                                       val groupId = neg(NegVars.GroupId)
                                       val profId =  neg(NegVars.ProfessorId)
                                       val classId =  ClassRoomId.Unassigned
                                       val clazz = Class(discipline(neg), prop.day, start, end, groupId, profId, classId)
                                       tt.put(prop.day, start, end, clazz)
                                   }.getOrElse(Left(new IllegalArgumentException(s"`end` is out of range: $endT")))
  }

  def putClass(prop: ClassesProposalMessage[Time]) = putClassIn(prop, timetable)
}

object CommonAgentDefs{

  case class Timeouts( )

  trait PutClassesInterface {
    self: NegotiatingAgent =>

    def putClassIn(prop: ClassesProposalMessage[Time], tt: MutableTimetable[Class[Time]]): Either[IllegalArgumentException, Unit]
  }

  def counterpartOpt(neg: Negotiation) = neg.get(OneToOneNegotiation.NegotiatingWith)
  def counterpart(neg: Negotiation)    = counterpartOpt(neg).get

  def disciplineOpt(neg: Negotiation)  = neg.get(NegVars.Discipline)
  def discipline(neg: Negotiation)     = disciplineOpt(neg).get

}








trait AgentsTime{
  agent: NegotiatingAgent =>

  type Time = feh.tec.agents.schedule.Time

  implicit def timeDescriptor = AgentsTime.tDescr

  val timetable = new MutableTimetable[Class[Time]]
}

object AgentsTime{
  implicit val tDescr = Time.descriptor(8*60, 22*60, 30)
}


trait TimeConstraints extends AgentsTime{
  agent: NegotiatingAgent =>

  def timetable: TimeTableRead[Time, Class[Time]]


  /** check that there is no class at given time
    *  ensure that will end before the closing time
    */
  def satisfiesConstraints(day: DayOfWeek, time: Time, length: Int): Boolean = {
    satisfiesConstraints(time, length) && !timetable.busyAt(day, time, timeDescriptor.plus(time, length))
  }

  /** ensure that will end before the closing time */
  def satisfiesConstraints(time: Time, length: Int): Boolean = {
    timeDescriptor.toMinutes(time) + length <= timeDescriptor.toMinutes(timeDescriptor.ending)
  }

}



trait CommonUtilityDrivenDefs extends CommonAgentDefs with TimeConstraints with UtilityDriven{
  agent: NegotiatingAgent with DynamicNegotiations with NegotiationReactionBuilder =>

  def assessedThreshold(neg: Negotiation): Double


  protected def weightedPriority(proposal: ProposalType) = negotiation(proposal)(NegVars.DisciplinePriority)

  def satisfiesConstraints(msg: ClassesProposalMessage[Time]): Boolean =
    satisfiesConstraints(msg.day, msg.time, msg.length)

  def utilityAcceptanceThreshold(neg: Negotiation)   = assessedThreshold(neg)
  def utilityAcceptanceThreshold(neg: NegotiationId) = utilityAcceptanceThreshold(negotiation(neg))
}