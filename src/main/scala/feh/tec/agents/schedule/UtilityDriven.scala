package feh.tec.agents.schedule

import feh.tec.agents.comm.negotiations.{Issues, Proposals}
import feh.tec.agents.comm.{Negotiation, NegotiatingAgent}
import feh.tec.agents.comm.agent.NegotiationReactionBuilder
import feh.tec.agents.schedule.CommonAgentDefs.PutClassesInterface
import feh.tec.agents.schedule.Messages._
import feh.util._

trait UtilityDriven extends UtilityDrivenGoal { // with CommonAgentProposalAssessment
  agent: NegotiatingAgent with CommonAgentDefs with NegotiationReactionBuilder =>


  type MessageType  = ClassesMessage
  type ProposalType = ClassesProposalMessage[Time]

  def negotiationTime: NegotiationTime

  protected def currentGoalHolder = timetable

  protected def acceptProposal(prop: ProposalType) = {
    val neg = negotiation(prop)
    neg.set(Issues.Vars.Issue(Vars.Day))       (prop.day)
    neg.set(Issues.Vars.Issue(Vars.Time[Time]))(prop.time)
    neg.set(Issues.Vars.Issue(Vars.Length))    (prop.length)

    ClassesAcceptance[Time](neg.id, prop.uuid)
  }

  def nextProposalFor(neg: Negotiation): ProposalType

  protected def rejectProposal(prop: ProposalType) = {
    val neg = negotiation(prop)
    val p = nextProposalFor(neg)
    val cprop = ClassesCounterProposal(neg.id, prop.uuid, p.day, p.time, p.length)
    awaitResponseFor(cprop)
    cprop
  }

  protected def assess(prop: ClassesProposalMessage[Time], neg: Negotiation) =
    utility(negotiationTime, currentGoalHolder, prop)

  /** Assesses the proposal and guards it in the timetable if it passes.
    */
  protected def handleClassesProposalMessage(prop: ClassesProposalMessage[Time], neg_ : Negotiation) =
    utilityDrivenProposalHandling(prop) match {
      case acc: ClassesAcceptance[Time]                               => putClass(prop).ensuring(_.isRight)
                                                                         Right(acc)
      case rej: ClassesProposalMessage[Time] with Proposals.Rejection => Left(rej)
    }

  private var _utilityChangeHistory: Seq[(Double, ProposalType)] = Nil

  override protected def beforeAccept(prop: ProposalType, utility: Double) = {
    super.beforeAccept(prop, utility)
    _utilityChangeHistory +:= utility -> prop

  }

  def utilityChangeHistory = _utilityChangeHistory

  override def reportTimetable() = reportTo ! TimetableReport( ImmutableTimetable(timetable.asMap)
                                                             , goalCompletion       = Option(goalAchievement(timetable))
                                                             , utilityChangeHistory = utilityChangeHistory
                                                             )
}

trait UtilityDrivenGoal extends UtilityDrivenAgent{
  agent: NegotiatingAgent with PutClassesInterface =>

  type GoalHolder = MutableTimetable[Class[Time]]


  protected def assumeProposal(gh: GoalHolder, proposal: ProposalType) = proposal match {
    case prop: ClassesProposalMessage[Time] => gh.copy $$ (putClassIn(prop, _))
  }
}