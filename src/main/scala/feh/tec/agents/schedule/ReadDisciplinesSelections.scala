package feh.tec.agents.schedule

import feh.tec.agents.schedule.util.XLSHelper
import feh.util.Path
import feh.util.file._
import org.apache.poi.hssf.usermodel.HSSFWorkbook


//case class DisciplinesSelection(disciplineKey: String, disciplineName: String, nOfStudents: Int)

object ReadDisciplinesSelections extends XLSHelper{
  type NOfStudents = Int
  type DisciplinesSelections = Map[Discipline, NOfStudents]

  def fromXLSX(file: Path, sheet: Option[String]): DisciplinesSelections = ???

  def fromXLS(path: Path, sheetName: String = null): DisciplinesSelections = withBook(path){
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

/*def fromXLS(path: Path, sheetName: String = null): Seq[DisciplinesSelection] = withBook(path){
    implicit book =>
      withSheet.byNameOrFirst(sheetName){
        implicit sheet =>
          forRows(from = 1){ // skip first row
            row =>
              DisciplinesSelection(disciplineKey  = row.getCell(0).getStringCellValue,
                                   disciplineName = row.getCell(1).getStringCellValue,
                                   nOfStudents    = row.getCell(2).getNumericCellValue.toInt
              )
           }
      }
    }*/
}
