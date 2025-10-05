// lib/ReporterFromExcel.groovy
import groovy.json.JsonOutput
import java.util.Locale
import java.security.MessageDigest

class ReporterFromExcel {

  // JVM-level guard (survives multiple calls in same run)
  private static volatile boolean ALREADY_RAN = false

  void generate(def context, def log) {
    // Guard #1: JVM-level
    if (ALREADY_RAN) {
      log.warn "[REPORT] ReporterFromExcel already ran in this execution (static guard). Skipping."
      return
    }
    ALREADY_RAN = true

    // Guard #2: context-level
    if (context['__REPORTER_FROM_EXCEL_DONE__'] == Boolean.TRUE) {
      log.warn "[REPORT] ReporterFromExcel already marked done in context. Skipping."
      return
    }
    context['__REPORTER_FROM_EXCEL_DONE__'] = Boolean.TRUE

    // ----- Resolve libs loaded by BootFlow -----
    def Lib     = context['Lib']      as Class
    def Columns = context['Columns']  as Class
    assert Lib && Columns : "BootFlow must load Lib & Columns before reporting"

    // ----- Paths -----
    File projDir = new File(context.expand('${projectDir}'))
    File libDir  = new File(projDir, 'lib')
    File dataDir = new File(projDir, 'test-data')
    File logsDir = new File(dataDir, 'Results_Logs'); logsDir.mkdirs()

    // ----- Choose files: MULTIPLE_DATA_FILES *or* SINGLE_DATA_FILE (exclusive) -----
    def tc = context.testCase
    String listProp = tc.getPropertyValue('MULTIPLE_DATA_FILES')?.trim()
    List<File> files = []

    if (listProp) {
      files = listProp.split(/\s*,\s*/).collect { new File(dataDir, it) }
      log.info "[REPORT] Using MULTIPLE_DATA_FILES (${files.size()})"
    } else {
      String single = null
      try { single = Lib.dataFile(context, 'SINGLE_DATA_FILE', 'CASE') } catch (Throwable ignore) {}
      if (single) {
        files = [ new File(single) ]
        log.info "[REPORT] Using SINGLE_DATA_FILE: ${new File(single).name}"
      }
    }

    // File-level de-dup (canonical, case-insensitive)
    files = files.findAll { it?.exists() }
                 .collect { it.canonicalFile }
                 .unique { it.canonicalPath.toLowerCase(Locale.ROOT) }

    if (!files) { log.warn "[REPORT] No data files found under ${dataDir.absolutePath}"; return }
    log.info "[REPORT] Files considered: ${files*.name}"

    // ----- Load ResultHtml dynamically -----
    def gcl = new GroovyClassLoader(this.class.classLoader)
    def ResultHtmlClass = gcl.parseClass(new File(libDir,'ResultHtml.groovy').text, 'ResultHtml.groovy')

    File reportFile = new File(logsDir, "report_${System.currentTimeMillis()}_from_excel.html")
    def report = ResultHtmlClass.newInstance(reportFile)
    def showToc = (tc.getPropertyValue('REPORT_TOC') ?: 'true').equalsIgnoreCase('true')
    try { report.showToc = showToc } catch (ignore) {}

    // ----- Build report from Excel columns (row de-dup + counters) -----
    def seenIdx   = new HashSet<String>() // (file,rowIndex)
    def seenSig   = new HashSet<String>() // content signature
    int added = 0, skippedIdx = 0, skippedSig = 0, skippedEmpty = 0

    files.each { f ->
      List<Map> rows = Lib.readXlsx(f.absolutePath)
      final String fileKey = f.canonicalPath.toLowerCase(Locale.ROOT)
      log.info "[REPORT] ${f.name}: rows=${rows.size()}"

      rows.eachWithIndex { row, i ->
        // de-dup by (file,row)
        String idxKey = fileKey + "::" + (i+1)
        if (!seenIdx.add(idxKey)) { skippedIdx++; return }

        // de-dup by content too (if a second identical list sneaks in)
        String sig = md5(JsonOutput.toJson(row))
        if (!seenSig.add(sig)) { skippedSig++; return }

        String flowName = (row[Columns.NAMES.FLOW] ?: '').toString()
        String workflow = (row['Workflow'] ?: '').toString()
        String jsonBlob = (row['JSON Response'] ?: '').toString()
        String result   = (row['Result'] ?: 'SKIPPED').toString()
        String status   = normalizeStatus(result)

        // skip completely empty rows
        if (!flowName && !workflow && !jsonBlob && !result) { skippedEmpty++; return }

        report.addFlowStart([
          row     : i + 1,
          flowName: flowName ?: '(no flow)',
          status  : status,
          refNo   : (row['RefNo'] ?: row['reNo'] ?: row['REF'] ?: ''),
          dataFile: f.name
        ])

        report.addStep([
          name    : "From Excel",
          request : workflow,
          response: pretty(jsonBlob),
          status  : statusForStep(status)
        ])

        report.addFlowEnd()
        added++
      }
    }

    report.finish()
    log.info "[REPORT] HTML written: ${reportFile.absolutePath}"
    log.info "[REPORT] Sections added=${added}, skipped by index=${skippedIdx}, skipped by content=${skippedSig}, skipped empty rows=${skippedEmpty}"
  }

  private static String normalizeStatus(String s) {
    if (!s) return 'UNKNOWN'
    def v = s.trim().toUpperCase()
    if (['PASS','PASSED','SUCCESS'].contains(v)) return 'PASS'
    if (['FAIL','FAILED'].contains(v))           return 'FAIL'
    if (['SKIP','SKIPPED','DID NOT RUN','DID_NOT_RUN','NA'].contains(v)) return 'SKIPPED'
    return v
  }

  private static String statusForStep(String overall) {
    switch (overall?.toUpperCase()) {
      case 'PASS': return 'PASS'
      case 'FAIL': return 'FAIL'
      default    : return 'UNKNOWN'
    }
  }

  private static String pretty(String raw) {
    if (!raw) return ''
    try { return JsonOutput.prettyPrint(raw) } catch (ignore) { return raw }
  }

  private static String md5(String s) {
    def md = MessageDigest.getInstance("MD5")
    md.update((s ?: '').getBytes('UTF-8'))
    return md.digest().encodeHex().toString()
  }
}
