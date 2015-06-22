package feh.tec.agents.schedule.io

import feh.tec.agents.schedule.{Discipline, ProfessorId}
import feh.util._

//@deprecated("only for TestApp")
//case class ProfCanTeach(profKey: String, disciplines: Seq[String])

object ProfsCanTeach extends XLSHelper{
  type DisciplineCode = String
  type IsFullTime     = Boolean
  type ProfsCanTeach  = Map[(ProfessorId, IsFullTime), Seq[Discipline]]


  def read(file: Path, sheetName: String = null): ProfsCanTeach = {
    val discByProf = withBook(file){
      implicit book =>
        withSheet.byNameOrFirst(sheetName){
          implicit sheet =>
            forRows(from = 1){
              row =>
                val disciplineCodePrefix = row.getCell(5).getStringCellValue
                val disciplineCodeSuffix = row.getCell(6).getStringCellValue
                val disciplineName = row.getCell(7).getStringCellValue
                val disciplineCode = disciplineCodePrefix + disciplineCodeSuffix

                val classesPerWeek = row.getCell(9).getNumericCellValue.toInt
                val labsPerWeek = row.getCell(10).getNumericCellValue.toInt

                val profTag = row.getCell(35).getStringCellValue
                val profFullTime = Option(row.getCell(40)).exists(_.getStringCellValue == "Planta")

                (ProfessorId(profTag), profFullTime) ->
                  Discipline(disciplineCode, disciplineName, classesPerWeek*60, labsPerWeek*60)
            }
        }
    }

    discByProf.groupBy(_._1).mapValues(_.map(_._2).distinct)
  }
}
