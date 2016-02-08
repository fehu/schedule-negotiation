package feh.tec.agents.schedule

import feh.tec.agents.comm.AgentId
import feh.tec.agents.comm.agent.{Coherence => GCoherence, AgentActor, Negotiating}
import feh.tec.agents.schedule.Messages.ClassesProposal
import feh.util.InUnitInterval

import scala.concurrent.{ExecutionContext, Future}

/** U. schedule negotiating agent coherence integration. */
trait Coherence extends GCoherence{
  agent: AgentActor with Negotiating with CommonAgentDefs =>

  /** Agent's action. */
  type Action

  protected implicit def coherenceExecContext: ExecutionContext

  type InformationPiece = InfPiece

  trait InfPiece
  case class ProposalInf(prop: ClassesProposal[Time]) extends InfPiece{
    def discipline = neg.get(NegVars.Discipline).get
    def professor  = neg.get(NegVars.ProfessorId).get
    def group      = neg.get(NegVars.ProfessorId).get

    private def neg = negotiation(prop)
  }

  /** The set of current [[ClassesProposal]]s. See [[contexts.Beliefs.defaultGraph]]. */
  protected def currentProposals: Set[ClassesProposal[Time]]

  /** See [[contexts.External.ExternalOpinion]]. */
  protected def askCounterpartsOpinion(over: Graph): contexts.External#Value

  /** The contexts. */
  object contexts{

    /** Possible values: [[Corroborates]], [[Contradicts]], [[SameClassInstance]]. */
    sealed trait DiscreteCValue { def value: Int }
    case object Corroborates      extends DiscreteCValue { def value = 1  }
    case object Contradicts       extends DiscreteCValue { def value = -1 }
    case object SameClassInstance extends DiscreteCValue { def value = 0  }

    protected trait Context0[C <: Context[C]] extends Context[C]{
      self: C =>

      /** Threshold for [[divideInfGraph]]. */
      def divideGraphThreshold: Double

      def process = g => Future {
        divideInfGraph(g :+: defaultGraph, self: C, divideGraphThreshold)
      }
    }


    /** Beliefs context. */
    class Beliefs extends Context0[Beliefs]{
      /** [[Corroborates]] | [[Contradicts]] | [[SameClassInstance]] */
      type Value = DiscreteCValue

      /** The threshold is 0 (exclusive). */
      def divideGraphThreshold = 0

      /** The current proposals. */
      def defaultGraph = newGraph(currentProposals.map(ProposalInf))

      /** The only relation: [[TimeConsistence]]. */
      lazy val binaryRelations: Set[RelationBinary] = Set(new TimeConsistence)

      /** None. */
      def wholeRelations = Set.empty

      /** Time Consistence binary relation. */
      class TimeConsistence extends RelationBinary{
        def apply(v1: InfPiece, v2: InfPiece) = v1 -> v2 match {
          case (p1: ProposalInf, p2: ProposalInf) =>
            if (isSameClass(p1, p2))            SameClassInstance
            else if (classesIntersect(p1, p2))  Contradicts
            else                                Corroborates
        }

        private def isSameClass(p1: ProposalInf, p2: ProposalInf) =
          p1.discipline == p2.discipline &&
          p1.group      == p2.group      &&
          p1.professor  == p2.professor

        private def classesIntersect(p1: ProposalInf, p2: ProposalInf) ={
          val (start1, end1) = classStartEnd(p1.prop)
          val (start2, end2) = classStartEnd(p2.prop)

          p1.prop.day == p2.prop.day &&
            ( start1 <= end2 || start2 <= end1 )
        }
        private def classStartEnd(p: ClassesProposal[Time]) = {
          val start = timeDescriptor.toMinutes(p.time)
          val end = start + p.length

          start -> end
        }

      }
    }

    /** Obligations context.
      * Requires [[Context.binaryRelations]] and [[Context.wholeRelations]] definition,
      *
      * @param defaultGraph graph with <i>obligations information</i>.
      */
    abstract class Obligations(val defaultGraph: Graph) extends Context0[Obligations]
    {
      type FailDescription

      /** Corroborates: `true`|`false`, maybe description (if Contradicts). */
      type Value = (Boolean, Some[FailDescription])

      /** The threshold is 0. */
      def divideGraphThreshold = 0
    }


    /** Preferences context.
      * Requires [[Context.binaryRelations]] and [[Context.wholeRelations]] definition,
      *
      * @param defaultGraph graph with <i>preferences information</i>.
      * @param preferencesThreshold get <i>preferences threshold</i>.
      */
    abstract class Preferences(val defaultGraph: Graph,
                               preferencesThreshold: () => Double) extends Context0[Preferences]
    {
      /** Preference value is within (0, 1]. */
      type Value = InUnitInterval.Excluding0

      /** See [[preferencesThreshold]] constructor argument. */
      def divideGraphThreshold = preferencesThreshold()
    }


    /** External context.
      *
      * @param satisfactionThreshold get <i>satisfaction threshold</i>.
      */
    class External (val satisfactionThreshold: () => Double) extends Context0[External]{
      /** counterpart's ID, [the graph's node, coherence value, some info.]. */
      type Value = (AgentId, List[(InformationPiece, InUnitInterval, Any)])

      /** No default graph. */
      def defaultGraph = emptyGraph

      /** No binary relations. */
      def binaryRelations = Set.empty

      /** The only relation: [[ExternalOpinion]]. */
      lazy val wholeRelations: Set[RelationWhole] = Set(new ExternalOpinion)

      /** The external opinion is asked using [[askCounterpartsOpinion]].
        * It is the main reason the context's [[Context.Result]] type is a [[Future]].
        */
      class ExternalOpinion extends RelationWhole{
        def apply(v1: Graph) = askCounterpartsOpinion(v1)
      }

      /** See [[satisfactionThreshold]] constructor argument. */
      def divideGraphThreshold = satisfactionThreshold()

    }

    /** Intentions context.
      * Accumulates the [[SolutionCandidate]]s in order to select the next action.
      */
    class Intentions extends AccumulatingContextImpl[Intentions] {
      type Input = SolutionCandidate[_]
      type AResult = Action

      def processAccumulated() = ???
    }

  }

}
