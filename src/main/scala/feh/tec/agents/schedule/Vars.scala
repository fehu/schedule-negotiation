package feh.tec.agents.schedule

import feh.tec.agents.comm.Var

object Vars{
  val Discipline = Var[String]("Discipline")

  sealed trait PropOrRecall
  case object New extends PropOrRecall
  case object Recall extends PropOrRecall

  val PropOrRecall = Var[PropOrRecall]("PropOrRecall")
}
