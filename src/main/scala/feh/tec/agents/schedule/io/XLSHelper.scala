package feh.tec.agents.schedule.io

import java.io.InputStream

import feh.util.Path
import feh.util.file._
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.{Row, Sheet, Workbook}
import org.apache.poi.xssf.usermodel.XSSFWorkbook

trait XLSHelper {
  def withBook[R](path: Path)(f: Workbook => R): R = {
    val newWB = path.ext.toLowerCase match {
      case "xls"  => new HSSFWorkbook(_: InputStream)
      case "xlsx" => new XSSFWorkbook(_: InputStream)
    }

    f(path.file.withInputStream(newWB).get)
  }

  object withSheet{
    def byNameOrFirst[R](name: String)(f: Sheet => R)(implicit book: Workbook): R =
      f(Option(name).map(book.getSheet).getOrElse(book.getSheetAt(0)))
  }

  def forRows[R](from: Int, to: Int)(f: Row => R)(implicit sheet: Sheet): IndexedSeq[R] =
    for{
      i <- from until to
      row = sheet.getRow(i)
      if row.getCell(0) != null
    } yield  f(row)

  def forRows[R](from: Int)(f: Row => R)(implicit sheet: Sheet): IndexedSeq[R] = forRows(from, sheet.getPhysicalNumberOfRows)(f)
  def forRows[R](f: Row => R)(implicit sheet: Sheet): IndexedSeq[R] = forRows(0)(f)
}
