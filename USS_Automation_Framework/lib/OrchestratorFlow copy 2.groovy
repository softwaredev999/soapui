import com.eviware.soapui.support.types.StringToObjectMap

/**
 * Orchestrator.groovy
 *
 * What this does:
 * - Runs a list of TestCases (by name) inside one TestSuite, in the same order you give.
 * - Lets you pass simple key/value props once, and those props are set at Project, Suite, and TestCase scopes,
 *   so your requests can use ${#Project#...}, ${#TestSuite#...}, ${#TestCase#...}.
 * - Collects a small result for each TestCase (name, status, time in ms).
 * - If stopOnFail = true, it stops running the rest when one TestCase does not finish.
 *
 * Common usage:
 * - runCases(project, "USS APIs", ["Request AA","Approve AA"], [userId:"u1"], true)
 * - runFlowByName(project, registryMap, "FLOW_REQUEST_APPROVE", [userId:"u1"])
 */

class Orchestrator {

  /**
   * Run specific TestCases, in order, inside one TestSuite.
   *
   * @param project     the SoapUI project object
   * @param suiteName   name of the TestSuite that holds the TestCases
   * @param caseNames   list of TestCase names to run, in the order you want
   * @param props       simple key/value map you want available during this run
   * @param stopOnFail  if true, stop when a case does not finish
   * @return            a List of Maps like [case:'Name', status:'FINISHED', timeMs:1234]
   */
  static List<Map> runCases(def project, String suiteName,
                            List<String> caseNames,
                            Map<String,String> props = [:],
                            boolean stopOnFail = true) {

    def out = []  // this will store small results per TestCase

    // find the suite (fail fast if typo or missing)
    def suite = project.getTestSuiteByName(suiteName)
    if (!suite) throw new IllegalArgumentException("Suite not found: ${suiteName}")

    // make the incoming props visible at higher scopes once per run
    // so requests can use ${#Project#key} or ${#TestSuite#key}
    props?.each { k, v ->
      def val = (v ?: "")
      project.setPropertyValue(k, val)
      suite.setPropertyValue(k, val)
    }

    // go through each TestCase name in the order given
    for (String tcName : caseNames) {
      def tc = suite.getTestCaseByName(tcName)
      if (!tc) throw new IllegalArgumentException("TestCase not found: ${tcName} (suite: ${suiteName})")

      // set the same props at TestCase scope (most REST bodies read from here)
      props?.each { k, v -> tc.setPropertyValue(k, (v ?: "")) }

      // run the TestCase now (synchronously = wait until it finishes)
      long t0 = System.currentTimeMillis()
      def runner = tc.run(new StringToObjectMap(), false) // false means synchronous
      long t1 = System.currentTimeMillis()

      // collect a simple result row
      def status = runner.status?.toString()
      out << [case: tcName, status: status, timeMs: (t1 - t0)]

      // if you want to stop on first problem, do it here
      if (stopOnFail && !'FINISHED'.equalsIgnoreCase(status)) break
    }

    return out
  }

  /**
   * Convenience: run a flow by name using a small registry map.
   * The registry entry should look like: [suite:'USS APIs', cases:['Request AA','Approve AA']]
   *
   * @param project     the SoapUI project object
   * @param registry    Map of flowName -> [suite:'...', cases:[...]]
   * @param flowName    which flow to run from the registry
   * @param props       props to make available during this run
   * @param stopOnFail  stop remaining cases if any case does not finish
   * @return            same list of small result maps as runCases
   */
  static List<Map> runFlowByName(def project, Map<String, Map> registry,
                                 String flowName, Map<String,String> props = [:],
                                 boolean stopOnFail = true) {

    // look up the definition (suite name + list of case names)
    def defn = registry[flowName]
    if (!defn) throw new IllegalArgumentException("Unknown flow: ${flowName}")

    // pass through to runCases with the suite and cases from the registry
    return runCases(
      project,
      defn.suite as String,
      (defn.cases as List<String>),
      props,
      stopOnFail
    )
  }
}
