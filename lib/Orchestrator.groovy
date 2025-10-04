// lib/Orchestrator.groovy
import com.eviware.soapui.support.types.StringToObjectMap

class Orchestrator {

  /**
   * Run a sequence of TestCases (by name) inside a TestSuite.
   * @param project       SoapUI project (context.testCase.testSuite.project)
   * @param suiteName     Target TestSuite name
   * @param caseNames     List<String> of TestCase names in desired order
   * @param props         Map<String,String> values to set on each TestCase (e.g., id/name)
   * @param stopOnFail    boolean, stop workflow on first failure
   * @return List<Map>    [{case:'TestCase 1', status:'FINISHED', timeMs:1234}, ...]
   */
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

      // set inputs
      props?.each { k, v -> tc.setPropertyValue(k, v ?: "") }

      long t0 = System.currentTimeMillis()
      def runner = tc.run(new StringToObjectMap(), false) // false = sync
      long t1 = System.currentTimeMillis()

      def rec = [case: tcName, status: runner.status?.toString(), timeMs: (t1 - t0)]
      out << rec

      // optional: collect failures
      if (stopOnFail && !runner.status?.toString()?.equalsIgnoreCase('FINISHED')) {
        return out // short-circuit on first failure
      }
    }
    return out
  }
}
