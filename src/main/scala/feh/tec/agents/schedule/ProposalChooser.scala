package feh.tec.agents.schedule

import akka.actor.ActorLogging
import feh.tec.agents.comm.{NegotiationId, Negotiation, NegotiatingAgent}
import feh.tec.agents.schedule.Messages.ClassesProposal
import feh.util._

import scala.annotation.tailrec
import scala.collection.immutable.TreeMap
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration


/** Generates valid (satisfying the constraints) proposals.
  *
  * Presents methods for guarding rejected proposals.
  */
trait ProposalChooser{
  agent: NegotiatingAgent with UtilityDriven =>

  protected type Millis = Long

  protected type RejectedProposalsCache = mutable.HashMap[NegotiationId, TreeMap[Millis, ProposalType]]

  protected lazy val rejectedProposalsCache =
    mutable.HashMap.empty[NegotiationId, TreeMap[Millis, ProposalType]]

  protected lazy val unconditionallyRejectedProposalsCache =
    mutable.HashMap.empty[NegotiationId, TreeMap[Millis, ProposalType]]


  /** Guards a rejected message to avoid repeating */
  def noteProposalRejection(prop: ProposalType, isUndonditional: Boolean): Unit ={
    val t = System.currentTimeMillis()
    val negId = prop.negotiation
    val cache = if (isUndonditional) unconditionallyRejectedProposalsCache else rejectedProposalsCache

    val tCache = rejectedProposalsCache.getOrElseUpdate(negId, TreeMap.empty)
    cache(negId) = tCache + (t -> prop)
  }

  /** Discards old conditional rejections (not marked with
    * [[feh.tec.agents.comm.negotiations.Proposals.Rejection.isUnconditional]]) from the guarded ones.
    */
  def discardOld(time: FiniteDuration): Unit = {
    val t = System.currentTimeMillis() - time.toMillis

    for ((negId, cache) <- rejectedProposalsCache) {
      val newCache = cache.dropWhile(_._1 < t)

      if (newCache.isEmpty) rejectedProposalsCache -= negId
      else rejectedProposalsCache += negId -> newCache
    }
  }

  def wasRejected(prop: ProposalType): Boolean = {
    def exists(cache: RejectedProposalsCache) = cache.get(prop.negotiation).exists(_.exists(_._2 == prop))

    exists(unconditionallyRejectedProposalsCache) || exists(rejectedProposalsCache)
  }

}


trait RandomProposalChooser extends ProposalChooser{
  agent: NegotiatingAgent with UtilityDriven with TimeConstraints =>


  protected def randomProposalFor(neg: Negotiation, lackingMinutes: Int) =
    fetchUntil(
      satisfiesConstraints,
      Stream.continually{
        fetchUntil(
          not compose wasRejected,
          Stream.continually{
            val l = randomLength(lackingMinutes)
            ClassesProposal(neg.id, randomDay(), randomTime(l), l)
          }
        )
      }
    )

  protected def randomDay() = (DaysOfWeek.values - DaysOfWeek.Sun).randomChoice.get

  protected def randomTime(length: Int) = fetchUntil(satisfiesConstraints(_: Time, length), timeDescriptor.randomly)

  protected def randomLength(lackingMinutes: Int) = possibleLengths(lackingMinutes).randomChoice.get


  @tailrec
  protected final def fetchUntil[T](cond: T => Boolean, it: Iterable[T], maxTries: Int = 100): T = {
    val res = it.head
    if(cond(res)) res
    else if(maxTries == 0) sys.error("couldn't find a value satisfying the condition")
    else fetchUntil(cond, it.tail, maxTries - 1)
  }

  protected def lengthDiscr = timeDescriptor.step

  protected def possibleLengths(totalLength: Int) = {
    assert(totalLength % lengthDiscr == 0, s"discipline minutes $totalLength cannot be divided by $lengthDiscr with integer result")
    val n = totalLength / lengthDiscr
    for(i <- 1 to n) yield i*lengthDiscr
  }

}

object RandomProposalChooser{

  trait Group extends RandomProposalChooser{
    agent: NegotiatingAgent with UtilityDriven with TimeConstraints =>

    def nextProposalFor(neg: Negotiation): ProposalType = {
      val d = neg(NegVars.Discipline)

      val assignedLength = currentGoalHolder.all.size
      val lackingMinutes = d.classes - assignedLength * timeDescriptor.step

      randomProposalFor(neg, lackingMinutes)
    }
  }


  trait Professor extends RandomProposalChooser {
    agent: NegotiatingAgent with UtilityDriven with TimeConstraints =>

    def nextProposalFor(neg: Negotiation): ProposalType = {
      val d = neg(NegVars.Discipline)
      randomProposalFor(neg, d.classes)
    }
  }


}