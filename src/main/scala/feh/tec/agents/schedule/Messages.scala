package feh.tec.agents.schedule

import java.util.UUID

import feh.tec.agents.comm.negotiations.{Var, Proposals}
import feh.tec.agents.comm._

object Messages {
  /** Used to calculate discipline priority */
  case class CounterpartsFound( negotiation : NegotiationId
                              , count       : Int
                              , uuid        : UUID              = UUID.randomUUID()  )
                              (implicit val sender: NegotiatingAgentRef) extends NegotiationMessage{
    val tpe = "CounterpartsFound"
    val asString = s"I've found $count counterparts"
  }

  case class DisciplinePriorityEstablished( negotiation : NegotiationId
                                            , priority  : Float
                                            , uuid      : UUID            = UUID.randomUUID()  )
                                          (implicit val sender: NegotiatingAgentRef) extends NegotiationMessage{
    val tpe = "DisciplinePriorityEstablished"
    val asString = s"discipline priority si $priority"
  }



  trait ClassesProposalMessage {
    self: Proposals.ProposalMessage =>
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
  }
  
  case class StartingNegotiation(discipline: Discipline, counterpart: NegotiatingAgentRef)
                                (implicit val sender: AgentRef)
    extends Report
  {
    def isSevere = false
    val tpe = "Starting Negotiation"
    val asString = s"over $discipline with $counterpart"
  }
}
