package feh.tec.agents.schedule2

import akka.actor.{ActorRefFactory, Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import feh.tec.agents.comm._
import feh.tec.agents.comm.agent.Coherence.GraphImplementation
import feh.tec.agents.comm.agent.{AgentActor, Coherence => GCoherence}
import feh.tec.agents.schedule2.ExternalKnowledge.{AnyProposal, ClassProposal, ConcreteProposal}
import feh.util.InUnitInterval

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag

/** University schedule negotiating agent coherence integration. */
trait Coherence extends GCoherence{
  agent: AgentActor =>

  /** Agent's action. */
  type Action

  type Time

  implicit def timeDescriptor: TimeDescriptor[Time]

//  protected implicit def coherenceExecContext: ExecutionContext

  type InformationPiece = ClassesRelatedInformation


//  /** Set of current proposals. @see [[contexts.Beliefs.defaultGraph]]. */
  protected def currentProposals: Future[Set[AnyProposal]]

  /** See [[contexts.External.ExternalOpinion]]. */
  protected def askCounterpartsOpinion(over: Graph): Future[contexts.External#Value]


  object contexts{

    /** Possible values: [[Corroborates]], [[Contradicts]], [[SameClassInstance]]. */
    sealed trait DiscreteCValue { def value: Int }
    case object Corroborates      extends DiscreteCValue { def value = 1  }
    case object Contradicts       extends DiscreteCValue { def value = -1 }
    case object SameClassInstance extends DiscreteCValue { def value = 0  }
    case object Incomplete      extends DiscreteCValue { def value = 0  }

    protected trait GeneratingContext[C <: Context[C]] extends Context[C]{
      self: C =>

      protected implicit def gPartition: GraphPartition[C]

      def process = g => Future {
        divideInfGraph(g :+: defaultGraph, self: C, threshold = 0).toSeq
      }
    }

    protected trait FilteringContext[C <: Context[C]] extends Context[C]{
      self: C =>

      def assessment: CoherenceAssessment

      /** Threshold for the filter. */
      def filterThreshold: Double


      def process = g => Future{
        val coh = assessment.assessCoherence(self: C, g)
        if (coh > filterThreshold) ThisSolutionCandidate.ThisSolutionSuccess(g, self: C, coh.excluding0) :: Nil
        else Nil
      }
    }


    /** Beliefs context. */
    class Beliefs(innerCmdTimeout: FiniteDuration)(implicit val gPartition: GraphPartition[Beliefs]) extends GeneratingContext[Beliefs]{
      /** [[Corroborates]] | [[Contradicts]] | [[SameClassInstance]] */
      type Value = DiscreteCValue

      /** The threshold is 0 (exclusive). */
      def divideGraphThreshold = 0

      /** The current proposals. */
      def defaultGraph = Await.result(currentProposals.map(newGraph), innerCmdTimeout)

      /** The only relation: [[TimeConsistence]]. */
      lazy val binaryRelationsWithin: Set[RelationBinary] = Set(new TimeConsistence)

      /** Has none. */
      def binaryRelationsWithDefault = Set()

      /** None. */
      def wholeRelations = Set.empty

      def toDouble = _.value.toDouble

      /** Time Consistence binary relation. */
      class TimeConsistence extends RelationBinary{


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

    /** Obligations context.
      * Requires [[Context.binaryRelationsWithin]] and [[Context.wholeRelations]] definition,
      *
      * @param defaultGraph graph with <i>obligations information</i>.
      */
    abstract class Obligations(val defaultGraph: Graph)
                              (implicit val assessment: CoherenceAssessment) extends FilteringContext[Obligations]
    {
      type FailDescription

      /** Corroborates: `true`|`false`, maybe description (if Contradicts). */
      type Value = Option[(Boolean, Some[FailDescription])]

      /** The threshold is 0. */
      def filterThreshold = 0
    }


    /** Preferences context.
      * Requires [[Context.binaryRelationsWithin]] and [[Context.wholeRelations]] definition,
      *
      * @param defaultGraph graph with <i>preferences information</i>.
      * @param preferencesThreshold get <i>preferences threshold</i>.
      */
    abstract class Preferences(val defaultGraph: Graph,
                               preferencesThreshold: () => Double)
                              (implicit val assessment: CoherenceAssessment) extends FilteringContext[Preferences]
    {
      /** Preference value is within (0, 1]. */
      type Value = Option[InUnitInterval.Excluding0]

      /** See [[preferencesThreshold]] constructor argument. */
      def filterThreshold = preferencesThreshold()
    }


    /** External context.
      *
      * @param satisfactionThreshold get <i>satisfaction threshold</i>.
      */
    class External (val satisfactionThreshold: () => Double, externalTimeout: FiniteDuration)
                   (implicit val assessment: CoherenceAssessment)
      extends FilteringContext[External]
    {
      /** counterpart's ID, [the graph's node, coherence value, some info.]. */
      type Value = Map[NegotiatingAgentId, InUnitInterval] // (AgentId, List[(InformationPiece, InUnitInterval, Any)])

      /** No default graph. */
      def defaultGraph = emptyGraph

      /** No binary relations. */
      def binaryRelationsWithin = Set.empty

      /** Has none. */
      def binaryRelationsWithDefault = Set()

      /** The only relation: [[ExternalOpinion]]. */
      lazy val wholeRelations: Set[RelationWhole] = Set(new ExternalOpinion)

      /** The external opinion is asked using [[askCounterpartsOpinion]].
        * It is the main reason the context's [[Context.Result]] type is a [[Future]].
        */
      class ExternalOpinion extends RelationWhole{
        def apply(v1: Graph) = Await.result(askCounterpartsOpinion(v1), externalTimeout)
      }

      /** See [[satisfactionThreshold]] constructor argument. */
      def filterThreshold = satisfactionThreshold()

      def toDouble = _.values.product // _._2.map(_._2).product
    }

    /** Intentions context.
      * Accumulates the [[SolutionCandidate]]s in order to select the next action.
      */
    class Intentions extends AccumulatingContextImpl[Intentions] {
      type Input = SolutionCandidate[_]
      type AResult = Action

      def processAccumulated() = ??? // todo
    }

  }

}

/** Agent implementation, based on coherence concept. Handler thread. */
trait CoherenceDrivenAgent extends Agent with Coherence{

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

trait CoherenceDrivenInterface[C <: Coherence]{
  /** Get opinion about a sub-graph. */
  def opinionAbout(g: C#Graph): InUnitInterval

  /** Check [[feh.tec.agents.schedule2.InternalKnowledge.Capacity]]. */
  def canBeAccepted(prop: ConcreteProposal[C#Time]): Boolean

  /** Check [[feh.tec.agents.schedule2.InternalKnowledge.Capacity]]. */
  def canBeAccepted(value: Discipline, nStudents: Int): Boolean

  /** Make next decision. Blocking.
    *
    * @return satisfied
    */
  def makeDecision(): Boolean

  def mkGraph(nodes: Set[C#InformationPiece]): C#Graph
}

object CoherenceDrivenAgent{
  sealed trait CInterfaceActor extends Actor

  class Main[C <: Coherence](c: CoherenceDrivenInterface[C]) extends CInterfaceActor{
    var waiting = false

    def receive = {
      case Command.MakeDecision =>
        waiting = false
        if(c.makeDecision()) waiting = true
        else self ! Command.MakeDecision

      case Command.Finished_? => sender() ! waiting
    }
  }


  /** Manages agent's known proposals. */
  class Proposals[C <: Coherence](c: CoherenceDrivenInterface[C])(implicit aRef: NegotiatingAgentRef) extends CInterfaceActor{
    val proposals = mutable.HashSet.empty[ConcreteProposal[C#Time]]
    val counterparts = mutable.HashMap.empty[Discipline, mutable.HashSet[NegotiatingAgentRef]]

    def receive = {
      case ClassProposalMsg(prop: ConcreteProposal[C#Time]) => sender() ! YesNoMsg(c.canBeAccepted(prop))
      case ClassProposalMsg(_)                              => sender() ! NotEnoughInfo

      case msg: AbstractProposalMsg =>
        val can = c.canBeAccepted(msg.value, msg.nStudents)
        if(can) counterparts.getOrElseUpdate(msg.value, new mutable.HashSet()) += msg.sender
        sender() ! YesNoMsg(can)


      case Command.AddProposal(prop) => proposals += prop.asInstanceOf[ConcreteProposal[C#Time]]
      case Command.GetProposals      => sender() ! c.mkGraph(proposals.toSet)
      case Command.GetCounterpartsCount(discipline, role) =>
        sender() ! counterparts.get(discipline).map(_.count(_.id.role == role)).getOrElse(0)
    }
  }

  class Opinions[C <: Coherence](c: CoherenceDrivenInterface[C])(implicit aRef: NegotiatingAgentRef) extends CInterfaceActor{
    def receive = {
      case OpinionMsg(props) => sender() ! CoherenceMsg(c.opinionAbout(c.mkGraph(props.asInstanceOf[Set[C#InformationPiece]])))
    }
  }

  class Argue[C <: Coherence](c: CoherenceDrivenInterface[C]) extends CInterfaceActor{
    def receive = Map.empty // todo
  }

  def mkProps[A <: CInterfaceActor : ClassTag](c: CoherenceDrivenInterface[_]) = Props(scala.reflect.classTag[A].runtimeClass, c)
}



class CoherenceDrivenAgentImpl(val id: NegotiatingAgentId,
                               implicit val timeDescriptor: TimeDescriptor[DTime],
                               val reportTo: SystemAgentRef,
                               internalTimeout: Timeout,
                               externalTimeout: Timeout,
                               aFactory: ActorRefFactory) extends CoherenceDrivenAgent with GraphImplementation{
  cAgent =>

  import CoherenceDrivenAgent._

  type Action = () => Unit
  type Time = DTime

  implicit val ref = NegotiatingAgentRef(id, self)

  val cInterface: CoherenceDrivenInterface[cAgent.type] = ???
  implicit def execContext = aFactory.dispatcher

  protected val main             = aFactory.actorOf(mkProps[Main[_]](cInterface))
  protected val argumentsHandler = aFactory.actorOf(mkProps[Argue[_]](cInterface))
  protected val opinionsHandler  = aFactory.actorOf(mkProps[Opinions[_]](cInterface))
  protected val proposalsHandler = aFactory.actorOf(mkProps[Proposals[_]](cInterface))


  /** See [[contexts.External.ExternalOpinion]]. */
  protected def askCounterpartsOpinion(over: Graph) = {
    val props = over.nodes.collect{ case prop: ConcreteProposal[_] => prop }
    val uniqueCounterparts = props.flatMap(_.sharedBetween) - this.ref

    implicit def to = externalTimeout
    val futures = Future.sequence{
      uniqueCounterparts.map(a => (a ? OpinionMsg(props)).mapTo[CoherenceMsg].map(a.id -> _.value))
    }
    futures.map(_.toMap)
  }

  /** Set of current proposals. @see [[contexts.Beliefs.defaultGraph]]. */
  protected def currentProposals = (proposalsHandler ? Command.GetProposals)(internalTimeout).mapTo


  val Reporting = new ReportingConfig(false, false, false)

  def stop() = ???

  def start() = ???



}