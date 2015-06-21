package feh.tec.agents.schedule.io

import feh.tec.agents.schedule.Discipline
import feh.util.Path


//case class DisciplinesSelection(disciplineKey: String, disciplineName: String, nOfStudents: Int)

@deprecated
object DisciplinesSelections extends XLSHelper{
  type NOfStudents = Int
  type DisciplinesSelections = Map[Discipline, NOfStudents]

  def read(path: Path, sheetName: String = null): DisciplinesSelections = withBook(path){
    implicit book =>
      withSheet.byNameOrFirst(sheetName){
        implicit sheet =>
          forRows(from = 1){ // skip first row
            row =>
              Discipline(code = row.getCell(0).getStringCellValue,
                         name = row.getCell(1).getStringCellValue

              ) -> row.getCell(2).getNumericCellValue.toInt
           }
      }.toMap
    }
}
