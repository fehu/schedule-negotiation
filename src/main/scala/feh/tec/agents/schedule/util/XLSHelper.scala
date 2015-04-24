package feh.tec.agents.schedule.util

import feh.util.Path
import feh.util.file._
import org.apache.poi.hssf.usermodel.{HSSFRow, HSSFSheet, HSSFWorkbook}

trait XLSHelper {
  def withBook[R](path: Path)(f: HSSFWorkbook => R): R = f(path.file.withInputStream(new HSSFWorkbook(_)).get)

  object withSheet{
    def byNameOrFirst[R](name: String)(f: HSSFSheet => R)(implicit book: HSSFWorkbook): R =
      f(Option(name).map(book.getSheet).getOrElse(book.getSheetAt(0)))
  }

  def forRows[R](from: Int, to: Int)(f: HSSFRow => R)(implicit sheet: HSSFSheet): IndexedSeq[R] =
    for{
      i <- from until to
      row = sheet.getRow(i)
      if row.getCell(0) != null
    } yield  f(row)

  def forRows[R](from: Int)(f: HSSFRow => R)(implicit sheet: HSSFSheet): IndexedSeq[R] = forRows(from, sheet.getPhysicalNumberOfRows)(f)
  def forRows[R](f: HSSFRow => R)(implicit sheet: HSSFSheet): IndexedSeq[R] = forRows(0)(f)
}
