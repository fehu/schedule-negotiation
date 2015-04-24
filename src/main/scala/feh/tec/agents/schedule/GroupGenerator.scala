package feh.tec.agents.schedule

import feh.tec.agents.schedule.ReadDisciplinesSelections.DisciplinesSelections

trait GroupGenerator {
  def divideIntoGroup(disciplinesSelections: DisciplinesSelections): Map[GroupId, Seq[Discipline]]
}

object GroupGenerator{
  def create(implicit policy: SchedulePolicy): GroupGenerator = new GroupGenerator {
    def divideIntoGroup(disciplinesSelections: DisciplinesSelections): Map[GroupId, Seq[Discipline]] = {
      disciplinesSelections.flatMap{
        case (d, n) => for{
                           i <- 1 to math.ceil(n.toDouble / policy.maxStudentsInGroup).toInt
                           name = d.code + "-" + i
                          }
          yield GroupId(name) -> Seq(d)
      }
    }
  }
}