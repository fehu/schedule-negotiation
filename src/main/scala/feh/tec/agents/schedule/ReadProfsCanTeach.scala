package feh.tec.agents.schedule

import feh.tec.agents.schedule.util.XLSHelper
import feh.util._

//@deprecated("only for TestApp")
//case class ProfCanTeach(profKey: String, disciplines: Seq[String])

@deprecated("only for TestApp")
object ReadProfsCanTeach extends XLSHelper{
  type DisciplineCode = String
  type ProfsCanTeach = Map[ProfessorId, Seq[Discipline]]


  def fromXLS(file: Path, disciplineByCode: DisciplineCode => Discipline, sheetName: String = null): ProfsCanTeach = {
    val discByProf = withBook(file){
      implicit book =>
        withSheet.byNameOrFirst(sheetName){
          implicit sheet =>
            forRows(from = 1){
              row =>
                row.getCell(1).getStringCellValue -> row.getCell(3).getStringCellValue
            }
        }
    }

    discByProf
      .groupBy(_._1)
      .mapValues(_.map(_._2).distinct.map(disciplineByCode))
      .mapKeys(ProfessorId)
  }
}
