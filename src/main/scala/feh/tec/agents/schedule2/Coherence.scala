package feh.tec.agents.schedule2

import akka.actor.{ActorRefFactory, Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import feh.tec.agents.comm._
import feh.tec.agents.comm.agent.Coherence.GraphImplementation
import feh.tec.agents.comm.agent.{AgentActor, Coherence => GCoherence}
import feh.tec.agents.schedule2.Coherence.Contexts.Intentions
import feh.tec.agents.schedule2.ExternalKnowledge.{AnyProposal, ClassProposal, ConcreteProposal}
import feh.tec.agents.schedule2.InternalKnowledge.{Capacity, Preference, Obligation}
import feh.util._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag

/** University schedule negotiating coherence definition. */
object Coherence extends GCoherence with GraphImplementation{

  type InformationPiece = ClassesRelatedInformation


  trait AgentInterface{
//    implicit def execContext: ExecutionContext

    def innerCmdTimeout: Timeout

    def externalTimeout: Timeout

    /** Set of current proposals. @see [[Contexts.Beliefs.defaultGraph]]. */
    def currentProposals: Future[Set[AnyProposal]]

    /** See [[Contexts.External.ExternalOpinion]]. */
    def askCounterpartsOpinion(over: Graph): Future[Contexts.External#Value]
  }


  object Contexts{
    /** Possible values: [[Corroborates]], [[Contradicts]], [[SameClassInstance]]. */
    sealed trait DiscreteCValue { def value: Int }
    case object Corroborates      extends DiscreteCValue { def value = 1  }
    case object Contradicts       extends DiscreteCValue { def value = -1 }
    case object SameClassInstance extends DiscreteCValue { def value = 0  }
    case object Incomplete      extends DiscreteCValue { def value = 0  }

    protected trait GeneratingContext[C <: Context[C]] extends Context[C]{
      self: C =>

      implicit def assessment: CoherenceAssessment

      protected implicit def gPartition: GraphPartition[C]

      def process = g => Future {
        divideInfGraph(g :+: defaultGraph, self: C, threshold = 0).toSeq
      }

      def assess(g: Input): ThisSolutionCandidate[C] = processFiltering(self: C, g, 0)
    }

    protected trait FilteringContext[C <: Context[C]] extends Context[C]{
      self: C =>

      implicit def assessment: CoherenceAssessment

      /** Threshold for the filter. */
      def filterThreshold: Double


      def process = g => Future{ processFiltering(self: C, g, filterThreshold) :: Nil }
    }

    protected def processFiltering[C <: Context[C]](c: C, g: C#Input, filterThreshold: Double )
                                                   (implicit assessment: CoherenceAssessment): ThisSolutionCandidate[C] =
    {
      val coh = assessment.assessCoherence(c, g)
      if (coh.doubleValue > filterThreshold) ThisSolutionCandidate.ThisSolutionSuccess(g, c, coh.excluding0)
      else ThisSolutionCandidate.ThisSolutionFailure(g, c, "failed at " + c.toString) // todo !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    }


    /** Beliefs context. */
    class Beliefs[Time](ag: AgentInterface)(implicit val gPartition: GraphPartition[Beliefs[Time]],
                                            val ex: ExecutionContext,
                                            val assessment: CoherenceAssessment)
      extends GeneratingContext[Beliefs[Time]]
    {
      /** [[Corroborates]] | [[Contradicts]] | [[SameClassInstance]] */
      type Value = DiscreteCValue

      /** The threshold is 0 (exclusive). */
      def divideGraphThreshold = 0

      /** The current proposals. */
      def defaultGraph = Await.result(ag.currentProposals.map(newGraph), ag.innerCmdTimeout.duration + 10.millis)

      /** The only relation: [[TimeConsistence]]. */
      lazy val binaryRelationsWithin: Set[Coherence.RelationBinary[Beliefs[Time]]] = Set(new TimeConsistence)

      /** Has none. */
      def binaryRelationsWithDefault = Set()

      /** None. */
      def wholeRelations = Set.empty

      def toDouble = Option apply _.value.toDouble

      /** Time Consistence binary relation. */
      class TimeConsistence extends Coherence.RelationBinary[Beliefs[Time]]{


        def apply(v1: InformationPiece, v2: InformationPiece) = (v1, v2) match {
          // same class instance
          case (c1: ClassProposal[Time], c2: ClassProposal[Time]) if c1 isSameUnderneath c2 => SameClassInstance
          // intersect?
          case (c1: ClassProposal[Time], c2: ClassProposal[Time]) if c1 intersects c2       => Contradicts
          // otherwise
          case (c1: ClassProposal[Time], c2: ClassProposal[Time])                           => Corroborates
          // one of the nodes isn't a `ClassProposal`
          case _                                                                            => Incomplete
        }

      }

    }

    trait ObligationFailed { def message: String }

    /** Obligations context.
      * Requires [[Context.binaryRelationsWithin]] and [[Context.wholeRelations]] definition,
      *
      * @param defaultGraph graph with <i>obligations information</i>.
      */
    abstract class Obligations(val defaultGraph: Graph)
                              (implicit val assessment: CoherenceAssessment, val ex: ExecutionContext)
      extends FilteringContext[Obligations]
    {
      /** Corroborates: `true`|`false`, maybe description (if Contradicts). */
      type Value = Option[(Boolean, Option[ObligationFailed])]

      /** The threshold is 0. */
      def filterThreshold = 0

      def toDouble = _.map(_._1 match { case true => 1; case false => 0})

    }


    /** Preferences context.
      * Requires [[Context.binaryRelationsWithin]] and [[Context.wholeRelations]] definition,
      *
      * @param defaultGraph graph with <i>preferences information</i>.
      * @param preferencesThreshold get <i>preferences threshold</i>.
      */
    abstract class Preferences(val defaultGraph: Graph,
                               preferencesThreshold: () => InUnitInterval.Including)
                              (implicit val assessment: CoherenceAssessment,
                               val ex: ExecutionContext) extends FilteringContext[Preferences]
    {
      /** Preference value is within (0, 1]. */
      type Value = Option[InUnitInterval.Excluding0]

      /** See [[preferencesThreshold]] constructor argument. */
      def filterThreshold = preferencesThreshold()

      def toDouble = _.map(_.d)
    }


    /** External context.
      *
      * @param satisfactionThreshold get <i>satisfaction threshold</i>.
      */
    class External (ag: AgentInterface, val satisfactionThreshold: () => InUnitInterval.Including)
                   (implicit val assessment: CoherenceAssessment, val ex: ExecutionContext)
      extends FilteringContext[External]
    {
      /** counterpart's ID, [the graph's node, coherence value, some info.]. */
      type Value = Map[NegotiatingAgentId, Coherence.SomeSolutionCandidate] // (AgentId, List[(InformationPiece, InUnitInterval, Any)])

      /** No default graph. */
      def defaultGraph = emptyGraph

      /** No binary relations. */
      def binaryRelationsWithin = Set.empty

      /** Has none. */
      def binaryRelationsWithDefault = Set()

      /** The only relation: [[ExternalOpinion]]. */
      lazy val wholeRelations: Set[RelationWhole[External]] = Set(new ExternalOpinion)

      /** The external opinion is asked using [[ag.askCounterpartsOpinion]].
        * It is the main reason the context's [[Context.Result]] type is a [[Future]].
        */
      class ExternalOpinion extends RelationWhole[External]{
        def apply(v1: Graph) = Await.result(ag.askCounterpartsOpinion(v1), ag.externalTimeout.duration + 10.millis)
      }

      /** See [[satisfactionThreshold]] constructor argument. */
      def filterThreshold = satisfactionThreshold().d

      def toDouble = input => Some apply (0d /: input.values.map((_: SomeSolutionCandidate).get)) {
        case (acc, SolutionSuccess(_, _, v0, prev)) => acc + v0.d * prev.map(_.v.d).product
        case (_, _: SolutionFailure[_]) => 0d
      } / input.size
//        Option apply _.values.product // _._2.map(_._2).product
    }

    /** Intentions context.
      * Accumulates the [[SolutionCandidate]]s in order to select the next action.
      */
    abstract class Intentions extends AccumulatingContextImpl[Intentions] {
      type Input = SolutionCandidate[_]
      type AResult = Boolean // Unit

//      def processAccumulated() = ??? // todo
    }

  }

}

/** Agent implementation, based on coherence concept. Handler thread. */
trait CoherenceDrivenAgent extends Agent with Coherence.AgentInterface{

  /** Main thread: coherence evaluation and acction selection. */
  protected val main: ActorRef
  /** Service thread: handling opinions. */
  protected val opinionsHandler: ActorRef
  /** Service thread: handling proposals. */
  protected val proposalsHandler: ActorRef
  /** Service thread: handling yield arguments. */
  protected val argumentsHandler: ActorRef


  /** Forwarding messages. */
  def messageReceived = {
    case proposal: ProposalMsg => proposalsHandler.forward(proposal)
    case opinion:  OpinionMsg  => opinionsHandler.forward(opinion)
    case argument: ArgueMsg    => argumentsHandler.forward(argument)
//    case response: ResponseMsg => main.forward(response)
  }


}

trait CoherenceDrivenInterface[Time]{
  /** Get opinion about a sub-graph. */
  def opinionAbout(g: Coherence.Graph): Future[Coherence.SomeSolutionCandidate]

  /** Check [[feh.tec.agents.schedule2.InternalKnowledge.Capacity]]. */
  def canBeAccepted(prop: ConcreteProposal[Time]): Boolean

  /** Check [[feh.tec.agents.schedule2.InternalKnowledge.Capacity]]. */
  def canBeAccepted(value: Discipline, nStudents: Int): Boolean

  /** Make next decision. Blocking.
    *
    * @return satisfied
    */
  def makeDecision(timeout: Timeout): Boolean

  def mkGraph(nodes: Set[Coherence.InformationPiece]): Coherence.Graph
}

object CoherenceDrivenAgent{
  sealed trait CInterfaceActor extends Actor

  class Main[Time](c: CoherenceDrivenInterface[Time], decisionMakingTimeout: Timeout) extends CInterfaceActor{
    var waiting = false

    def receive = {
      case Command.MakeDecision =>
        waiting = false
        if(c.makeDecision(decisionMakingTimeout)) waiting = true
        else self ! Command.MakeDecision

      case Command.Finished_? => sender() ! waiting
    }
  }


  /** Manages agent's known proposals. */
  class Proposals[Time](c: CoherenceDrivenInterface[Time],
                        implicit val aRef: NegotiatingAgentRef,
                        implicit val exc: ExecutionContext)
    extends CInterfaceActor
  {
    val proposals = mutable.HashSet.empty[ConcreteProposal[Time]]
    val counterparts = mutable.HashMap.empty[Discipline, mutable.HashSet[NegotiatingAgentRef]]

    def receive = {
      case ClassProposalMsg(prop: ConcreteProposal[Time]) => sender() ! YesNoMsg(c.canBeAccepted(prop))
      case ClassProposalMsg(_)                              => sender() ! NotEnoughInfo

      case msg: AbstractProposalMsg =>
        val can = c.canBeAccepted(msg.value, msg.nStudents)
        if(can) counterparts.getOrElseUpdate(msg.value, new mutable.HashSet()) += msg.sender
        sender() ! YesNoMsg(can)


      case Command.AddProposal(prop) => proposals += prop.asInstanceOf[ConcreteProposal[Time]]
      case Command.GetProposals      => sender() ! c.mkGraph(proposals.toSet)
      case Command.GetCounterpartsCount(discipline, role) =>
        sender() ! counterparts.get(discipline).map(_.count(_.id.role == role)).getOrElse(0)
    }
  }

  class Opinions[Time](c: CoherenceDrivenInterface[Time],
                       implicit val aRef: NegotiatingAgentRef,
                       implicit val exc: ExecutionContext
                      )
    extends CInterfaceActor
  {
    def receive = {
      case OpinionMsg(props) => c.opinionAbout(c.mkGraph(props.asInstanceOf[Set[Coherence.InformationPiece]])) map {
        c => sender() ! CoherenceMsg(c)
      }
    }
  }

  class Argue[Time](c: CoherenceDrivenInterface[Time],
                    implicit val aRef: NegotiatingAgentRef,
                    implicit val exc: ExecutionContext)
    extends CInterfaceActor
  {
    def receive = Map.empty // todo
  }

  def mkProps[A <: CInterfaceActor : ClassTag](c: CoherenceDrivenInterface[_])
                                              (implicit aRef: NegotiatingAgentRef, exc: ExecutionContext)
    = Props(scala.reflect.classTag[A].runtimeClass, c, aRef, exc)
}


case class Knowledge(capacities: Set[Capacity],
                     obligations: Set[Obligation],
                     preferences: Set[Preference])



abstract class CoherenceDrivenAgentImpl(aFactory: ActorRefFactory)
  extends CoherenceDrivenAgent
  with CoherenceDrivenInterface[CoherenceDrivenAgentImpl]
{
  cAgent =>

  import Coherence._

  override val id: NegotiatingAgentId
//  implicit val timeDescriptor: TimeDescriptor[DTime]
//  val reportTo: SystemAgentRef
  val internalTimeout: Timeout
  val externalTimeout: Timeout
  val decisionMakingTimeout: Timeout

  val internalKnowledge: Knowledge

  val externalSatisfactionThreshold: () => InUnitInterval.Including

  import CoherenceDrivenAgent._

  type Action = () => Unit
  type Time = DTime

  implicit val ref = NegotiatingAgentRef(id, self)

  val cInterface = this
//    def canBeAccepted(value: _root_.feh.tec.agents.schedule2.Discipline, nStudents: Int) = ???
//
//    def mkGraph(nodes: Set[ClassesRelatedInformation]) = ???
//  }


  trait contexts{
//    type Obligations <: Contexts.Beliefs[Time]
//    type Preferences <: Contexts.Preferences

    implicit val cohAssessment = new Coherence.CoherenceAssessment.CoherenceAssessmentImpl()
    implicit val graphPartition = new Coherence.GraphPartition.GraphPartitionByAggregation[Coherence.Contexts.Beliefs[Time]]()

    val obligations: Contexts.Obligations
    val preferences: Contexts.Preferences

    val beliefs = new Coherence.Contexts.Beliefs(cAgent)
    val external = new Coherence.Contexts.External(cAgent, externalSatisfactionThreshold)
    val intentions = new Intentions {
      def processAccumulated(): AResult = ??? // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    }
  }

  val contexts: contexts

  implicit def execContext = aFactory.dispatcher

  protected def mainProps = Props(scala.reflect.classTag[Main[_]].runtimeClass, cInterface, decisionMakingTimeout)
  protected val main             = aFactory.actorOf(mainProps)
  protected val argumentsHandler = aFactory.actorOf(mkProps[Argue[_]](cInterface))
  protected val opinionsHandler  = aFactory.actorOf(mkProps[Opinions[_]](cInterface))
  protected val proposalsHandler = aFactory.actorOf(mkProps[Proposals[_]](cInterface))


  /** See [[Coherence.Contexts.External.ExternalOpinion]]. */
  def askCounterpartsOpinion(over: Graph) = {
    val props = over.nodes.collect{ case prop: ConcreteProposal[_] => prop: ConcreteProposal[_] }
    val uniqueCounterparts = props.flatMap(_.sharedBetween) - this.ref

    implicit def to = externalTimeout
    val futures = Future.sequence{
      uniqueCounterparts.map(a => (a ? OpinionMsg(props)).mapTo[CoherenceMsg].map(a.id -> _.value))
    }
    futures.map(_.toMap)
  }

  /** Set of current proposals. @see [[Coherence.Contexts.Beliefs.defaultGraph]]. */
  def currentProposals = (proposalsHandler ? Command.GetProposals)(internalTimeout).mapTo


  val Reporting = new ReportingConfig(false, false, false)

  def start() = {
    println("Starting " + this)
    main ! Command.MakeDecision
  }

  def stop() = ??? // TODO


  def mkGraph(nodes: Set[ClassesRelatedInformation]) = newGraph(nodes)

  // CoherenceDrivenInterface

//  def canBeAccepted(value: Discipline, nStudents: Int) = ???
//  def canBeAccepted(prop: ConcreteProposal[Time]) = ???
//  def opinionAbout(g: CoherenceDrivenAgentImpl#Graph) = ???
//  def makeDecision() = ???
}