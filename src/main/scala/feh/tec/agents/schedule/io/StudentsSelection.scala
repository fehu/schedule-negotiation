package feh.tec.agents.schedule.io

import feh.tec.agents.schedule.{Discipline, StudentId}
import feh.util.Path

object StudentsSelection extends XLSHelper{
  type DisciplineName = String
  type MinutesPerWeek = Int

  def read(path: Path, sheetName: String = null): Map[StudentId, Map[DisciplineName, MinutesPerWeek]] = withBook(path) {
    implicit book =>
      withSheet.byNameOrFirst(sheetName) {
        implicit sheet =>
          val selectionSeq = forRows(from = 1){ // skip first row
            row =>
             val tag            = row.getCell(0).getStringCellValue
             val career         = row.getCell(4).getStringCellValue
             val disciplineName = row.getCell(8).getStringCellValue
             val hoursPerWeek   = row.getCell(9).getNumericCellValue match {
               case 0 => row.getCell(10).getNumericCellValue
               case x => x
             }

             StudentId(tag, career) -> (disciplineName, hoursPerWeek.toInt * 60)
         }
         selectionSeq.groupBy(_._1).mapValues(_.map(_._2).toMap)

    }
  }
}
