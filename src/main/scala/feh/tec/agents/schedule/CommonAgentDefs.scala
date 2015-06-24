package feh.tec.agents.schedule

import akka.actor.ActorLogging
import akka.util.Timeout
import feh.tec.agents.comm.Message.HasValues
import feh.tec.agents.comm.Negotiation.VarUpdated
import feh.tec.agents.comm.Report.StateChanged
import feh.tec.agents.comm._
import feh.tec.agents.comm.agent.Negotiating.DynamicNegotiations
import feh.tec.agents.comm.negotiations.Establishing.{NegotiationEstablishingMessage, NegotiationProposition, NegotiationAcceptance, NegotiationRejection}
import feh.tec.agents.comm.negotiations.Var
import feh.tec.agents.schedule.CommonAgentDefs._
import feh.tec.agents.schedule.Messages.{ClassesProposalMessage, TimetableReport}
import feh.tec.agents.util.OneToOneNegotiation

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