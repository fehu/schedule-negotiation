package feh.tec.agents.schedule2

import akka.util.Timeout
import feh.tec.agents.schedule2.Coherence._
import feh.util.InUnitInterval

import scala.concurrent.Future

/**
  *
  */
trait GenericCoherenceDrivenAgent extends CoherenceDrivenAgentImpl{
  cAgent =>

  def relations: AgentRelations
  def preferencesThreshold: () => InUnitInterval.Including

  lazy val filteringCtxs = Seq(
    Coherence.ContextContainer(contexts.obligations),
    Coherence.ContextContainer(contexts.preferences)
  )

  def opinionAbout(g: Coherence.Graph): Future[Coherence.SomeSolutionCandidate] = {
    contexts.beliefs.assess(g) match {
      case fail: ThisSolutionCandidate.ThisSolutionFailure[Contexts.Beliefs[Time]] => Future { fail.withNoPrevious }
      case succ: ThisSolutionCandidate.ThisSolutionSuccess[Contexts.Beliefs[Time]] =>
        val initial = succ.withNoPrevious

        Coherence.propagateSolutions(Set(SomeSolutionSuccess(initial)), filteringCtxs) map {
          case set if set.size == 1 => set.head.asInstanceOf // TODO: asInstanceOf
          case _                    => Coherence.SolutionFailure(g, null, null, None) // TODO: null !!!!!!!!!!!!!!!!!!!!
        }
    }
  }

  val contexts: contexts = new contexts{

    val obligations = new Contexts.Obligations(newGraph(internalKnowledge.obligations)) {
      def wholeRelations: Set[RelationWhole[Contexts.Obligations]] = relations.obligations.whole.asInstanceOf
      def binaryRelationsWithin: Set[RelationBinary[Contexts.Obligations]] = relations.obligations.binaryWithin.asInstanceOf
      def binaryRelationsWithDefault: Set[RelationBinary[Contexts.Obligations]] = relations.obligations.binaryWithDefaultGraph.asInstanceOf
    }

    val preferences = new Contexts.Preferences(newGraph(internalKnowledge.preferences), preferencesThreshold){
      def wholeRelations: Set[RelationWhole[Contexts.Preferences]] = relations.preferences.whole.asInstanceOf
      def binaryRelationsWithin: Set[RelationBinary[Contexts.Preferences]] = relations.preferences.binaryWithin.asInstanceOf
      def binaryRelationsWithDefault: Set[RelationBinary[Contexts.Preferences]] = relations.preferences.binaryWithDefaultGraph.asInstanceOf
    }

  }

  def innerCmdTimeout = internalTimeout

  /** Make next decision. Blocking.
    *
    * @return satisfied
    */
  def makeDecision(timeout: Timeout): Boolean = {
    val ctxs = Seq(
      ContextContainer(contexts.obligations),
      ContextContainer(contexts.preferences),
      ContextContainer(contexts.external)
    )

    contexts.intentions.resetAccumulated()
    for {
      initial <- contexts.beliefs.process(newGraph())
      x = initial.map(SomeSolutionSuccess apply _.asInstanceOf) // TODO: asInstanceOf
      candidates <- Coherence.propagateSolutions(x.toSet, ctxs)
    } contexts.intentions accumulate candidates.toSeq

    contexts.intentions.processAccumulated()
  }
}

object GenericCoherenceDrivenAgent{
  object Capacity{
    // For professors
    case class CanTeach(disciplines: Set[Discipline]) extends InternalKnowledge.Capacity
//    case class CanWork() extends InternalKnowledge.Capacity // TODO: For part-time professors

    // For students/groups
    case class SearchesDisciplines(disciplines: Set[Discipline]) extends InternalKnowledge.Capacity

    // For classrooms
  }
}