package feh.tec.agents.schedule.io

import feh.tec.agents.schedule.{Discipline, ProfessorId}
import feh.util._

//@deprecated("only for TestApp")
//case class ProfCanTeach(profKey: String, disciplines: Seq[String])

// todo: full-time or part-time ??
object ProfsCanTeach extends XLSHelper{
  type DisciplineCode = String
  type ProfsCanTeach = Map[ProfessorId, Seq[Discipline]]


  def fromXLS(file: Path, sheetName: String = null): ProfsCanTeach = {
    val discByProf = withBook(file){
      implicit book =>
        withSheet.byNameOrFirst(sheetName){
          implicit sheet =>
            forRows(from = 1){
              row =>
                val tag = row.getCell(1).getStringCellValue
                val disciplineName = row.getCell(2).getStringCellValue
                val disciplineCode = row.getCell(3).getStringCellValue

                ProfessorId(tag) -> Discipline(disciplineCode, disciplineName)
            }
        }
    }

    discByProf.groupBy(_._1).mapValues(_.map(_._2).distinct)
  }
}
