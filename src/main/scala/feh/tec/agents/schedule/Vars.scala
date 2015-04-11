package feh.tec.agents.schedule

import feh.tec.agents.comm.{NegotiationVar, NegotiatingAgentId, AgentId, Var}
import feh.tec.agents.schedule

import scala.collection.mutable

object Vars{
  val Discipline = Var[schedule.Discipline]("Discipline")

  sealed trait PropOrRecall
  case object New extends PropOrRecall
  case object Recall extends PropOrRecall

  val PropOrRecall = Var[PropOrRecall]("PropOrRecall")
}

object NegVars{
  object NewNegAcceptance extends NegotiationVar{ type T = mutable.Map[Discipline, Map[NegotiatingAgentId, Option[Boolean]]] }

  object Discipline extends NegotiationVar{ type T = schedule.Discipline }
}