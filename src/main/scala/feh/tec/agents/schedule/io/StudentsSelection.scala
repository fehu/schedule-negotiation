package feh.tec.agents.schedule.io

import feh.tec.agents.schedule.{Discipline, StudentId}
import feh.util.Path

object StudentsSelection extends XLSHelper{
  type DisciplineCode = String
  type MinutesPerWeek = Int

  def read(path: Path, sheetName: String = null): Map[StudentId, Map[DisciplineCode, MinutesPerWeek]] = withBook(path) {
    implicit book =>
      withSheet.byNameOrFirst(sheetName) {
        implicit sheet =>
          val selectionSeq = forRows(from = 1){ // skip first row
            row =>
              val tag            = row.getCell(0).getStringCellValue
              val career         = row.getCell(4).getStringCellValue

              val disciplineCodePrefix = row.getCell(5).getStringCellValue
              val disciplineCodeSuffix = row.getCell(6).getStringCellValue
              val disciplineCode = disciplineCodePrefix + disciplineCodeSuffix

              val hoursPerWeek   = row.getCell(9).getNumericCellValue match {
                case 0 => row.getCell(10).getNumericCellValue
                case x => x
              }

             StudentId(tag, career) -> (disciplineCode, hoursPerWeek.toInt * 60)
         }
         selectionSeq.groupBy(_._1).mapValues(_.map(_._2).toMap)

    }
  }
}
