// Driver: run one or more Excel files, each row picks a flow and runs its test cases.
// - Loads Lib/Orchestrator/Columns/Flows via Boot (if not already loaded)
// - Builds the data file list (from DATA_FILES or TS_01_DATA_FILE)
// - For each file: read rows, pick flow (row "flow" or FLOW_DEFAULT), run, write Result/Workflow/JSON Response

log.info "++++++++++ MULTI-FILE DRIVER ++++++++++\n"

import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import com.eviware.soapui.impl.wsdl.teststeps.RestTestRequestStep
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequestStep

// ---------- Load library classes (Boot adds them to context if missing) ----------
def Lib          = context['Lib']
def Orchestrator = context['Orchestrator']
def Columns      = context['Columns']
def Flows        = context['Flows']
if (!(Lib instanceof Class) || !(Orchestrator instanceof Class) ||
    !(Columns instanceof Class) || !(Flows instanceof Class)) {
  evaluate(new File(new File(context.expand('${projectDir}')), 'lib/BootFlow.groovy'))
  Lib          = context['Lib']
  Orchestrator = context['Orchestrator']
  Columns      = context['Columns']
  Flows        = context['Flows']
}

// ---------- 1) Build the list of data files to run ----------
def dataDir  = new File(context.expand('${projectDir}'), 'test-data')
def listProp = context.testCase.getPropertyValue('DATA_FILES')    // comma-separated file names
List<File> files = []
if (listProp?.trim()) {
  files = listProp.split(/\s*,\s*/).collect { new File(dataDir, it) }
} else {
  def single = Lib.dataFile(context, 'TS_01_DATA_FILE', 'CASE')   // fallback to single file prop
  files = [ new File(single) ]
}
files = files.findAll { it.exists() }
if (files.isEmpty()) {
  throw new FileNotFoundException("No data files found. Checked: DATA_FILES and TS_01_DATA_FILE under ${dataDir.absolutePath}")
}
log.info "Data files to process (${files.size()}): ${files*.name}"

// ---------- 2) Default flow if row doesn't specify one ----------
def defaultFlow = context.testCase.getPropertyValue('FLOW_DEFAULT') ?: 'FLOW_REQUEST_APPROVE'

// ---------- Helpers for pretty-print and response extraction ----------
def tryPretty = { String raw ->
  if (!raw) return ''
  try { return JsonOutput.prettyPrint(raw) } catch (ignore) { return raw }
}
def lastResponse = { tc ->
  for (def step : tc.getTestStepList().reverse()) {
    try {
      if (step instanceof RestTestRequestStep || step instanceof WsdlTestRequestStep) {
        def resp = step.getTestRequest()?.getResponse()?.getContentAsString()
        if (resp) return resp
      }
    } catch (ignore) {}
  }
  return ''
}

// ---------- 3) Per-file execution (sequential to avoid xlsx write conflicts) ----------
def runFile = { File f ->
  final String path = f.absolutePath
  final List<Map> rows = Lib.readXlsx(path)
  log.info "\n=========[${f.name}] Rows read = ${rows.size()}=========\n"

  def project   = context.testCase.testSuite.project
  def workspace = project.workspace

  // cache so tokenCase runs ONCE per (project,suite,token) per file
  def tokenRan = new HashSet<String>()

  int passCnt = 0, failCnt = 0, skipCnt = 0

  rows.eachWithIndex { row, i ->
    def currentResult = (row['Result'] ?: '').toString().trim()

    // RUN only if the row is marked "DID NOT RUN" (or blank)
    if (currentResult.equalsIgnoreCase('DID NOT RUN') || currentResult == '') {

      // Build props from the entire row (headers -> values)
      Map<String,String> props = [:]
      row.each { k, v -> props[k] = (v ?: '').toString() }

      // Flow name from the row or default
      def flowName = (row[Columns.NAMES.FLOW] ?: defaultFlow).toString().trim()
      def defn = Flows.REGISTRY[flowName]
      if (!defn) throw new IllegalArgumentException("Unknown flow in row ${i+1}: ${flowName}")

      // Optional: ENV -> project mapping (uncomment to drive env by column)
      // def env = (row['ENV'] ?: context.testCase.getPropertyValue('ENV') ?: 'INT').toString().trim().toUpperCase()
      // def projectByEnv = ['INT':'INT ENV PROJECT','TEST':'TEST ENV PROJECT','ASSEMBLY':'ASSEMBLY ENV PROJECT']
      // if (projectByEnv[env]) props['__TARGET_PROJECT__'] = projectByEnv[env]

      // Resolve target for token logic
      def tpName   = (props['__TARGET_PROJECT__'] ?: defn.project ?: project.name) as String
      def suite    = (defn.suite as String)
      def token    = (defn.tokenCase ?: defn.token ?: null) as String

     log.info "[${f.name}] Row ${i+1}: flow=${flowName}, targetProject='${tpName}', suite='${suite}'"
	// Build props first
	//Map<String,String> props = [:]
	row.each { k, v -> props[k] = (v ?: '').toString() }
	// One-line, compact row dump
	def kv = props.collect { k, v -> "${k}=${v.replace('\n','\\n')}" }.join('; ')
	log.info "[${f.name}] Row ${i+1}: flow=${flowName}, targetProject='${tpName}', suite='${suite}' | RowData: ${kv}"

      // ----- Token once per file per target (optional, only if tokenCase is defined) -----
      if (token) {
        def key = "${tpName}::${suite}::${token}"
        if (!tokenRan.contains(key)) {
          // run token once
          def tp = workspace?.getProjectByName(tpName)
          if (!tp) throw new IllegalArgumentException("Target project not open: ${tpName}")
          Orchestrator.runCases(tp, suite, [token], props, true)
          tokenRan.add(key)
        }
        // skip token inside the flow for all rows
        props['__SKIP_TOKEN__'] = 'true'
      }

      try {
        // Make the entire row available as JSON if downstream cases need it
        project.setPropertyValue('ROW_JSON', new JsonBuilder(props).toString())

        // ---- Run the flow (cross-project aware) ----
//        def results = Orchestrator.runFlowByName(project, workspace, Flows.REGISTRY, flowName, props, true)
        def results = Orchestrator.runFlowByName(project, workspace, Flows.REGISTRY, flowName, props, true, log)

        log.info "[${f.name}] Row ${i+1} results: ${results}"

        // ---- Workflow summary (each on its own line) ----
        def workflowSummary = results.withIndex().collect { r, idx ->
          def ok = r.status?.equalsIgnoreCase('FINISHED')
          def label = ok ? 'PASSED' : (r.status ?: 'UNKNOWN')
          "${idx+1}. ${r.case} - ${label}"
        }.join('\n')
        Lib.writeCellByHeader(path, null, 'Workflow', i, workflowSummary)

        // ---- JSON Response blob (per executed case) ----
        def tp = workspace ? (workspace.getProjectByName(tpName) ?: project) : project
        def suiteObj = tp.getTestSuiteByName(suite)
        def pieces = []
        results.each { r ->
          def tc = suiteObj?.getTestCaseByName(r.case)
          def raw = tc ? lastResponse(tc) : ''
          def pretty = tryPretty(raw)
          int maxChars = 8000
          if (pretty.length() > maxChars) pretty = pretty.substring(0, maxChars) + "\n...[truncated]"
          pieces << "${r.case}:\n${pretty}"
        }
        def jsonBlob = pieces.join('\n\n')
        Lib.writeCellByHeader(path, null, 'JSON Response', i, jsonBlob)

        // ---- Overall PASS only if every case finished ----
        def pass = results && results.every { it.status?.equalsIgnoreCase('FINISHED') }
        Lib.writeCellByHeader(path, null, 'Result', i, pass ? 'PASS' : 'FAIL')
        if (pass) passCnt++ else failCnt++

      } catch (Throwable t) {
        log.error "[${f.name}] Row ${i+1} FAILED: ${t.message}", t
        Lib.writeCellByHeader(path, null, 'Result', i, 'FAIL')
        failCnt++
      }

    } else {
      // Count skips so the file summary is accurate
      skipCnt++
    }
  }

  log.info "[${f.name}] Summary: PASS=${passCnt}, FAIL=${failCnt}, SKIP=${skipCnt}"
}

// ---------- 4) Run every file ----------
files.each { runFile(it) }

log.info "++++++++++ DRIVER DONE ++++++++++"