import com.eviware.soapui.support.types.StringToObjectMap
import com.eviware.soapui.SoapUI

class Orchestrator {

  // --- dual logger helpers ---
  private static void info(def logger, String msg)  { if (logger) logger.info(msg);  SoapUI.log.info(msg) }
  private static void warn(def logger, String msg)  { if (logger) logger.warn(msg);  SoapUI.log.warn(msg) }
  private static void error(def logger, String msg) { if (logger) logger.error(msg); SoapUI.log.error(msg) }


  private static List<String> enabledCaseNames(def suite) {
    return suite.getTestCaseList()
      .findAll { tc ->
        try { !tc.isDisabled() } catch (ignored) {
          try { !tc.disabled } catch (ignored2) { true }
        }
      }
      .collect { it.name }
  }

  /**
   * Run a flow by name. Supports:
   * - cross-project via defn.project or props['__TARGET_PROJECT__']
   * - tokenCase: 'Get Token' (runs first unless props['__SKIP_TOKEN__']=='true')
   * - cases: explicit list OR '*' (all enabled in UI order)
   * - exclude: names to remove from main list (token is not excluded)
   */
  static List<Map> runFlowByName(def currentProject,
                                 def workspace,
                                 Map<String, Map> registry,
                                 String flowName,
                                 Map<String,String> props = [:],
                                 boolean stopOnFail = true,def logger = null) {
    // log.info "Orchestrator.runFlowByName(flowName='${flowName}', props=${props}, stopOnFail=${stopOnFail})"
    info(logger, "Orchestrator.runFlowByName(flowName='${flowName}', stopOnFail=${stopOnFail})")

    def defn = registry[flowName]
    if (!defn) throw new IllegalArgumentException("Unknown flow: ${flowName}")

    // Resolve target project strictly
    def targetProjectName = (props['__TARGET_PROJECT__'] ?: defn.project ?: currentProject.name) as String
    def targetProject = workspace?.getProjectByName(targetProjectName)
    if (!targetProject) {
      def open = (workspace?.projectList ?: []).collect { it.name }
      throw new IllegalArgumentException("Target project not open or name mismatch: '${targetProjectName}'. Open projects: ${open}")
    }

    // Resolve suite strictly
    def suiteName = defn.suite as String
    def suite = targetProject.getTestSuiteByName(suiteName)
    if (!suite) {
      def suites = targetProject.getTestSuiteList()*.name
      throw new IllegalArgumentException("Suite not found: '${suiteName}' (project: ${targetProject.name}). Suites here: ${suites}")
    }

    // Expand cases
    def token   = (defn.tokenCase ?: defn.token ?: null) as String
    def skipTok = ((props['__SKIP_TOKEN__'] ?: '') as String).equalsIgnoreCase('true')

    def excludes = ((defn.exclude ?: []) as List<String>).toSet()
    boolean wantsAll = defn.cases == '*' || (defn.cases instanceof List && defn.cases.size() == 1 && defn.cases[0] == '*')

    List<String> baseList
    if (wantsAll) {
      baseList = enabledCaseNames(suite)
    } else {
      baseList = (defn.cases as List<String>) ?: []
    }

    // Remove token from the "rest" (we add it at the front if present)
    if (token) baseList = baseList.findAll { it != token }
    // Apply excludes to the main list
    if (!excludes.isEmpty()) baseList = baseList.findAll { !excludes.contains(it) }

    // Final list = [token?] + baseList, but only if token exists & enabled AND not skipped
    def finalCases = []
    if (token && !skipTok && enabledCaseNames(suite).contains(token)) {
      finalCases << token
    }
    finalCases.addAll(baseList)

    if (finalCases.isEmpty()) {
      throw new IllegalStateException("No TestCases to run after expansion for flow '${flowName}' (project: ${targetProject.name}, suite: ${suiteName})")
    }

    return runCases(targetProject, suiteName, finalCases, props, stopOnFail)
  }

  /**
   * Run specific TestCases, in order, inside one TestSuite.
   */
  static List<Map> runCases(def targetProject, String suiteName,
                            List<String> caseNames,
                            Map<String,String> props = [:],
                            boolean stopOnFail = true) {

    def out = []
    def suite = targetProject.getTestSuiteByName(suiteName)
    if (!suite) throw new IllegalArgumentException("Suite not found: ${suiteName} (project: ${targetProject.name})")

    // Seed incoming props at Project & Suite scope
    props?.each { k, v ->
      def val = (v ?: "")
      targetProject.setPropertyValue(k, val)
      suite.setPropertyValue(k, val)
    }

    for (String tcName : caseNames) {
      def tc = suite.getTestCaseByName(tcName)
      if (!tc) throw new IllegalArgumentException("TestCase not found: ${tcName} (suite: ${suiteName}, project: ${targetProject.name})")

      // Also seed at TestCase scope
      props?.each { k, v -> tc.setPropertyValue(k, (v ?: "")) }

      long t0 = System.currentTimeMillis()
      def runner = tc.run(new StringToObjectMap(), false) // sync
      long t1 = System.currentTimeMillis()

      def status = runner.status?.toString()
      out << [case: tcName, status: status, timeMs: (t1 - t0)]

      if (stopOnFail && !"FINISHED".equalsIgnoreCase(status)) break
    }

    return out
  }
}
