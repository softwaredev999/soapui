import com.eviware.soapui.support.types.StringToObjectMap

class Orchestrator {

  static List<Map> runCases(def project, String suiteName,
                            List<String> caseNames,
                            Map<String,String> props = [:],
                            boolean stopOnFail = true) {
    def out = []
    def suite = project.getTestSuiteByName(suiteName)
    if (!suite) throw new IllegalArgumentException("Suite not found: ${suiteName}")

    caseNames.each { tcName ->
      def tc = suite.getTestCaseByName(tcName)
      if (!tc) throw new IllegalArgumentException("TestCase not found: ${tcName} (suite: ${suiteName})")

      props?.each { k, v -> tc.setPropertyValue(k, v ?: "") }
      long t0 = System.currentTimeMillis()
      def runner = tc.run(new StringToObjectMap(), false)
      long t1 = System.currentTimeMillis()
      out << [case: tcName, status: runner.status?.toString(), timeMs: (t1 - t0)]

      if (stopOnFail && !runner.status?.toString()?.equalsIgnoreCase('FINISHED')) return out
    }
    return out
  }

  // NEW: run a flow from Flows.REGISTRY by name
  static List<Map> runFlowByName(def project, Map<String, Map> registry,
                                 String flowName, Map<String,String> props = [:],
                                 boolean stopOnFail = true) {
    def defn = registry[flowName]
    if (!defn) throw new IllegalArgumentException("Unknown flow: ${flowName}")
    return runCases(project, defn.suite as String, (defn.cases as List<String>), props, stopOnFail)
  }
}
