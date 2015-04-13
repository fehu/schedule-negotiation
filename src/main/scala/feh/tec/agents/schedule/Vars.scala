package feh.tec.agents.schedule

import feh.tec.agents.comm.negotiations.Var
import feh.tec.agents.comm.{NegotiationVar, NegotiatingAgentId}
import feh.tec.agents.schedule

import scala.collection.mutable

object Vars{
  val Discipline = Var[schedule.Discipline]("Discipline")

  sealed trait PropOrRecall
  case object New extends PropOrRecall
  case object Recall extends PropOrRecall

  val PropOrRecall = Var[PropOrRecall]("PropOrRecall")

  val Day     = Var[DayOfWeek]("Day of week")
  def Time[T] = Var[T]        ("Class beginning time")
  val Length  = Var[Int]      ("Class length in minutes")
}

object NegVars{
  object NewNegAcceptance extends NegotiationVar{ type T = mutable.Map[Discipline, Map[NegotiatingAgentId, Option[Boolean]]] }

  object Discipline extends NegotiationVar{ type T = schedule.Discipline }

  object DisciplinePriority extends NegotiationVar{ type T = Float }
}