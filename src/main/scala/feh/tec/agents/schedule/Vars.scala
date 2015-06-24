package feh.tec.agents.schedule

import feh.tec.agents.comm.negotiations.{Issues, Var}
import feh.tec.agents.comm.{NegotiationVar, NegotiatingAgentId}
import feh.tec.agents.schedule

import scala.collection.mutable

object Vars{
  val Discipline = Var[schedule.Discipline]("Discipline")

//  sealed trait PropOrRecall
//  case object New extends PropOrRecall
//  case object Recall extends PropOrRecall

//  val PropOrRecall = Var[PropOrRecall]("PropOrRecall")

  val Day     = Var[DayOfWeek]("Day of week")
  def Time[T] = Var[T]        ("Class beginning time")
  val Length  = Var[Int]      ("Class length in minutes")

  val EntityId = Var[EntityId]("EntityId")
}

object NegVars{
  object NewNegAcceptance extends NegotiationVar{ type T = Map[NegotiatingAgentId, Option[Boolean]] }

  object Discipline extends NegotiationVar{ type T = schedule.Discipline }

  object DisciplinePriority extends NegotiationVar{ type T = Float }

//  object StudentId extends NegotiationVar{ type T = GroupId }
  object GroupId     extends NegotiationVar{ type T = GroupId     }
  object ProfessorId extends NegotiationVar{ type T = ProfessorId }
  object ClassRoomId extends NegotiationVar{ type T = ClassRoomId }
}