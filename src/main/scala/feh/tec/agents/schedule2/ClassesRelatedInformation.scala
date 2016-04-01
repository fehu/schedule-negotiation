package feh.tec.agents.schedule2

import feh.tec.agents.comm.NegotiatingAgentRef


/** Root type for [[feh.tec.agents.schedule2.Coherence#InformationPiece]] implementation. */
sealed trait ClassesRelatedInformation extends Equals

/** Internal/private agent's knowledge. */
trait InternalKnowledge extends ClassesRelatedInformation

/** External/shared knowledge. */
trait ExternalKnowledge extends ClassesRelatedInformation{
  def sharedBetween: Set[NegotiatingAgentRef]
}


object InternalKnowledge{
  trait Capacity   extends InternalKnowledge
  trait Obligation extends InternalKnowledge
  trait Preference extends InternalKnowledge
}

object ExternalKnowledge{
  /** Root type for proposals. */
  sealed trait AnyProposal extends ExternalKnowledge{
//    /** Id of the creator. */
//    def by: NegotiatingAgentRef

    def discipline: Discipline
  }

  /** A proposal with no specific time. */
  trait AbstractProposal extends AnyProposal
  /** A proposal with specific day, time and duration. */
  trait ConcreteProposal[Time] extends AnyProposal{
    implicit val timeDescriptor: TimeDescriptor[Time]
    import timeDescriptor._

    def day: DayOfWeek
    def begins: Time
    def duration: Time

    def ends: Time = plus(begins, duration)

    def intersects(that: ConcreteProposal[Time]) = this.day == that.day &&
      (  that.begins <= this.begins && this.begins >= that.ends
      || that.begins <= this.ends   &&   this.ends >= that.ends )
  }

  /** Links group, professor and classroom. */
  trait CompleteProposal extends AnyProposal{
    def group: NegotiatingAgentRef
    def professor: NegotiatingAgentRef
    def classroom: NegotiatingAgentRef

    def sharedBetween: Set[NegotiatingAgentRef] = Set(group, professor, classroom)

    /** Compares the linked agents and disciplines. */
    def isSameUnderneath(that: CompleteProposal) = this.discipline == that.discipline &&
                                                   this.group == that.group &&
                                                   this.professor == that.professor &&
                                                   this.classroom == that.classroom

  }

  type ClassProposal[Time] = ConcreteProposal[Time] with CompleteProposal


  case class CProposal[Time](day: DayOfWeek,
                             begins: Time,
                             duration: Time,
                             discipline: Discipline,
                             addressee: NegotiatingAgentRef)
                            (implicit val timeDescriptor: TimeDescriptor[Time],
                             sender: NegotiatingAgentRef)
    extends ConcreteProposal[Time]
  {
    def sharedBetween = Set(sender, addressee)
  }

  case class CCProposal[Time](day: DayOfWeek,
                              begins: Time,
                              duration: Time,
                              discipline: Discipline,
                              group: NegotiatingAgentRef,
                              classroom: NegotiatingAgentRef,
                              professor: NegotiatingAgentRef
                              )
                             (implicit val timeDescriptor: TimeDescriptor[Time])
    extends ConcreteProposal[Time] with CompleteProposal

  trait Opinion[Time] extends ExternalKnowledge {
    def by: NegotiatingAgentRef
    def about: Set[ClassProposal[Time]]
  }
}