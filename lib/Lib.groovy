import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.usermodel.WorkbookFactory

/**
 * Lib.groovy
 *
 * What this file is for:
 * - Small helper methods I can reuse in Groovy driver steps.
 * - Ping: prints a log line so I know my wiring works.
 * - dataFile: reads a property (PROJECT / SUITE / CASE), resolves a file path under projectDir/test-data,
 *             and returns a normalized absolute path string.
 * - readXlsx + helpers: read an .xlsx (first sheet) into List<Map<String,String>> using Apache POI.
 * - writeCellByHeader: write a value into a cell by header name (creates the header/row/column if missing).
 *
 * Notes:
 * - This expects Apache POI JARs installed (SoapUI OS: drop them in <soapui.home>/bin/ext and restart).
 * - Paths: on Windows I still log with forward slashes so logs look clean; Java can read backslash too.
 * - I’m not changing any code logic here, only adding comments.
 */

class Lib {

    /**
     * Simple log to prove the lib is loaded and I can call it from a driver.
     * @param scope the SoapUI script scope (usually "this" from a Groovy step) so log.info works
     * @param from  who is calling (free text for my logs)
     */
    static void ping(def scope, String from) {
        def msg = "[LIB] Ping from ${from}"
        scope.log.info(msg)                                   // Script Log
        // If needed later: SoapUI global log and stack traces
        // com.eviware.soapui.SoapUI.log(msg.toString())
        // com.eviware.soapui.SoapUI.logError(new Exception(msg))
    }

    /**
     * Get a data file path from a SoapUI property at a given scope.
     * scope = "PROJECT" | "SUITE" | "CASE"
     * - Reads the property value (e.g., "myfile.xlsx" or "sub/dir/file.csv")
     * - If it’s relative, resolves under ${projectDir}/test-data
     * - If it’s absolute, returns as-is
     * - Returns normalized absolute path with forward slashes (just for clean logs)
     *
     * @param context  SoapUI script context (has access to testCase/testSuite/project and ${projectDir})
     * @param propName the property name to read (e.g., DATA_FILE)
     * @param scope    "PROJECT", "SUITE", or "CASE"
     * @return         absolute path string (slashes normalized)
     */
    // scope: "PROJECT" | "SUITE" | "CASE"
    static String dataFile(def context, String propName, String scope) {
    String val
    switch (scope?.toUpperCase()) {
        case 'CASE':
            val = context.testCase?.getPropertyValue(propName); break
        case 'SUITE':
            val = context.testCase?.testSuite?.getPropertyValue(propName); break
        case 'PROJECT':
            val = context.testCase?.testSuite?.project?.getPropertyValue(propName); break
        default:
            throw new IllegalArgumentException("scope must be PROJECT, SUITE, or CASE")
    }
    if (!val?.trim()) {
        throw new IllegalStateException("Property '${propName}' is missing/blank at scope ${scope}")
    }

    // If the value is a file name (relative), resolve under projectDir/test-data
    def p = val.trim()
    File base = new File(context.expand('${projectDir}'), 'test-data')
    File f = (new File(p).isAbsolute()) ? new File(p) : new File(base, p)
    return f.absolutePath.replace('\\','/')
}

  /**
   * Read an .xlsx file (first sheet) into a list of maps.
   * - Row 0 is the header row (keys).
   * - Each following row becomes a Map<header -> text value>.
   * - Blank rows are skipped.
   * @param filePath absolute (or valid) file path to the .xlsx
   * @return List<Map<String,String>> rows
   */
  // One-shot loader → returns List<Map<String,String>>
  static List<Map<String,String>> readXlsx(String filePath) {
    FileInputStream fis = new FileInputStream(new File(filePath))
    Workbook wb = WorkbookFactory.create(fis)
    try {
      Sheet sheet = wb.getSheetAt(0)                // using first sheet by default
      def fmt = new DataFormatter()                 // formats any cell to String safely
      List<String> hdrs = headers(sheet, fmt)       // read header names from row 0
      return rowsAsMaps(sheet, hdrs, fmt)           // build list of maps for data rows
    } finally {
      wb.close()
      fis.close()                                   // close streams to avoid Windows locks
    }
  }

  /**
   * Read header names from row 0.
   * If row 0 is missing, throw a clear error.
   */
  static List<String> headers(Sheet sheet, DataFormatter fmt) {
    Row r0 = sheet.getRow(0)
    if (!r0) throw new IllegalStateException("No header row (row 0) found")
    def out = []
    r0.cellIterator().each { Cell c -> out << fmt.formatCellValue(c).trim() }
    return out
  }

  /**
   * Convert each data row (starting from row index 1) into a Map<header -> value>.
   * - Uses MissingCellPolicy to handle blanks.
   * - Skips rows that are truly empty (all values blank).
   */
  static List<Map<String,String>> rowsAsMaps(Sheet sheet, List<String> headers, DataFormatter fmt) {
    int lastRow = sheet.getLastRowNum()
    def data = []
    for (int r = 1; r <= lastRow; r++) {
      Row row = sheet.getRow(r)
      if (row == null) continue
      Map<String,String> m = [:]
      headers.eachWithIndex { h, c ->
        Cell cell = row.getCell(c, MissingCellPolicy.RETURN_BLANK_AS_NULL)
        m[h] = fmt.formatCellValue(cell)
      }
      // Skip truly empty lines (all blank)
      if (m.values().any { it != null && it.toString().trim() }) {
        data << m
      }
    }
    return data
  }


  /**
   * Write a String value into the row (0-based for data rows: 0=first data row under headers),
   * locating/creating the column by header text (case-insensitive).
   * If sheetName is null, uses the first sheet.
   *
   * Behavior (plain english):
   * - Looks for the header cell in row 0 (creates it if missing).
   * - Finds/creates the target data row (dataRowIndex 0 = physical row 1).
   * - Writes the value as a STRING cell.
   * - Tries to autosize the column (best effort).
   * - Saves the workbook.
   *
   * Tips:
   * - Keep the file closed in other apps (Excel) to avoid locks on Windows.
   * - This method opens then writes the same path; if you need multi-row writes, you can batch in memory yourself.
   */
  static void writeCellByHeader(String xlsxPath, String sheetName, String headerText, int dataRowIndex, String value) {
    FileInputStream fis = new FileInputStream(new File(xlsxPath))
    Workbook wb = WorkbookFactory.create(fis)
    fis.close() // close read stream ASAP (Windows file lock)

    try {
      Sheet sheet = (sheetName ? wb.getSheet(sheetName) : wb.getSheetAt(0))
      if (sheet == null) throw new IllegalStateException("Sheet not found: ${sheetName ?: '(index 0)'}")

      DataFormatter fmt = new DataFormatter()

      // 1) find/create header cell (row 0)
      Row header = sheet.getRow(0)
      if (header == null) header = sheet.createRow(0)

      int colIndex = -1
      int lastCellNum = Math.max(0, header.getLastCellNum() as int)
      for (int c = 0; c < lastCellNum; c++) {
        Cell hc = header.getCell(c, MissingCellPolicy.RETURN_BLANK_AS_NULL)
        def text = hc ? fmt.formatCellValue(hc) : ""
        if (text?.equalsIgnoreCase(headerText)) { colIndex = c; break }
      }
      if (colIndex < 0) {
        colIndex = lastCellNum                             // append a new column at the end
        Cell hc = header.createCell(colIndex, CellType.STRING)
        hc.setCellValue(headerText)
      }

      // 2) get/create the target data row (dataRowIndex 0 => sheet row 1)
      int physicalRow = dataRowIndex + 1
      Row row = sheet.getRow(physicalRow)
      if (row == null) row = sheet.createRow(physicalRow)

      // 3) write value (as STRING so it stays consistent in logs/files)
      Cell cell = row.getCell(colIndex, MissingCellPolicy.CREATE_NULL_AS_BLANK)
      cell.setCellType(CellType.STRING)
      cell.setCellValue(value ?: "")

      // (optional) autosize the column (ignore if the sheet type doesn’t support it)
      try { sheet.autoSizeColumn(colIndex) } catch (ignored) {}

      // 4) save workbook back to the same path
      FileOutputStream fos = new FileOutputStream(new File(xlsxPath))
      wb.write(fos)
      fos.close()
    } finally {
      wb.close() // always close the workbook
    }
  }
}
