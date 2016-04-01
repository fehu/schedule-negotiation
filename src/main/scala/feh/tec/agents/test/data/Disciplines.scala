package feh.tec.agents.test.data

import feh.tec.agents.schedule2.Discipline


object Disciplines {

  def timePerWeek = 2 * 60 // 2 hours
  // TODO: Labs

  lazy val A1 = Discipline("A1", "A1", timePerWeek, 0)
  lazy val A2 = Discipline("A2", "A2", timePerWeek, 0)
  lazy val A3 = Discipline("A3", "A3", timePerWeek, 0)
  lazy val A4 = Discipline("A4", "A4", timePerWeek, 0)
  lazy val A5 = Discipline("A5", "A5", timePerWeek, 0)
  
  lazy val B1 = Discipline("B1", "B1", timePerWeek, 0)
  lazy val B2 = Discipline("B2", "B2", timePerWeek, 0)
  lazy val B3 = Discipline("B3", "B3", timePerWeek, 0)
  lazy val B4 = Discipline("B4", "B4", timePerWeek, 0)
  lazy val B5 = Discipline("B5", "B5", timePerWeek, 0)
  
  lazy val C1 = Discipline("C1", "C1", timePerWeek, 0)
  lazy val C2 = Discipline("C2", "C2", timePerWeek, 0)
  lazy val C3 = Discipline("C3", "C3", timePerWeek, 0)
  lazy val C4 = Discipline("C4", "C4", timePerWeek, 0)
  lazy val C5 = Discipline("C5", "C5", timePerWeek, 0)

}
