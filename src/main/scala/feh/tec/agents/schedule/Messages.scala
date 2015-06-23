package feh.tec.agents.schedule

import java.util.UUID

import feh.tec.agents.comm.Message.HasValues
import feh.tec.agents.comm.negotiations.{Var, Proposals}
import feh.tec.agents.comm._
import feh.util.PrintIndents

object Messages {
  /** Used to calculate discipline priority */
  case class CounterpartsFound( negotiation : NegotiationId
                              , count       : Int
                              , uuid        : UUID              = UUID.randomUUID()  )
                              (implicit val sender: NegotiatingAgentRef) extends NegotiationMessage{
    val tpe = "CounterpartsFound"
    val asString = s"I've found $count counterpart(s)"
  }

  case class DisciplinePriorityEstablished( negotiation : NegotiationId
                                            , priority  : Float
                                            , uuid      : UUID            = UUID.randomUUID()  )
                                          (implicit val sender: NegotiatingAgentRef) extends NegotiationMessage{
    val tpe = "DisciplinePriorityEstablished"
    val asString = s"discipline priority si $priority"
  }



  trait ClassesProposalMessage extends Message{
    self: Proposals.ProposalMessage =>

    val myValues: Map[Var[Any], Any]
  }

  implicit def classesProposalHasValues = new HasValues[ClassesProposalMessage] {
    def values = _.myValues
  }


  case class ClassesProposal[Time]( negotiation: NegotiationId
                                  , day: DayOfWeek
                                  , time: Time
                                  , length: Int
                                  , extra: Map[Var[Any], Any] = Map.empty
                                  , uuid: UUID                = UUID.randomUUID() )
                                  (implicit val sender: NegotiatingAgentRef)
    extends Proposals.Proposal
    with ClassesProposalMessage
  {
    val myValues = extra +  ( Vars.Day        -> day
                            , Vars.Time[Time] -> time
                            , Vars.Length     -> length
                            )
  }

  case class ClassesAcceptance[Time]( negotiation : NegotiationId
                                    , respondingTo : UUID
                                    , myValues    : Map[Var[Any], Any] = Map.empty
                                    , uuid        : UUID               = UUID.randomUUID() )
                                  (implicit val sender: NegotiatingAgentRef)
    extends Proposals.Acceptance
    with ClassesProposalMessage

  case class ClassesRejection[Time]( negotiation : NegotiationId
                                     , respondingTo : UUID
                                     , myValues    : Map[Var[Any], Any] = Map.empty
                                     , uuid        : UUID               = UUID.randomUUID() )
                                   (implicit val sender: NegotiatingAgentRef)
    extends Proposals.Rejection
    with ClassesProposalMessage

  case class ClassesCounterProposal[Time] ( negotiation: NegotiationId
                                          , respondingTo: UUID
                                          , day: DayOfWeek
                                          , time: Time
                                          , length: Int
                                          , extra: Map[Var[Any], Any] = Map.empty
                                          , uuid: UUID                = UUID.randomUUID() )
                                        (implicit val sender: NegotiatingAgentRef)
    extends Proposals.CounterProposal
    with ClassesProposalMessage
  {
    val myValues = extra +  ( Vars.Day        -> day
                            , Vars.Time[Time] -> time
                            , Vars.Length     -> length
                            )
  }

  case class NoCounterpartFound(d: Discipline)(implicit val sender: AgentRef) extends Report{
    def isSevere = true
    val tpe = "No Counterpart Found"
    val asString = d.toString
    def underlyingMessage = None
  }
  
  case class StartingNegotiation(discipline: Discipline, counterpart: NegotiatingAgentRef)
                                (implicit val sender: AgentRef)
    extends Report
  {
    def isSevere = false
    val tpe = "Starting Negotiation"
    val asString = s"over $discipline with $counterpart"
    def underlyingMessage = None
  }

  case class TimetableReport(tt: ImmutableTimetable[Option[ClassId]])(implicit val sender: AgentRef)
    extends Report with PrintIndents
  {
    def isSevere = false
    val tpe = "Timetable Report"
    val asString = {
      implicit val p = newBuilder(4)
      printlni("="*20)

      nextDepth{
                 tt.asMap.mapValues(_.filter(_._2.isDefined).mapValues(_.get))
                 .foreach{
                           case (day, classes) =>
                             printlni(day + ":")
                             nextDepth{
                                        classes foreach{
                                          case (time, clazz) => printlni(s"$time --- $clazz")
                                        }
                                      }
                         }

               }
      printlni("="*20)

      p.mkString
    }
    def underlyingMessage = None
  }
}