/**
 * Flows.groovy
 *
 * What this file is for:
 * - A simple catalog of named “flows” so drivers/orchestrators can run a sequence of TestCases
 *   without hard-coding names in every script.
 * - Each flow points to ONE TestSuite (by name) and lists TestCases (by name) in the order to run.
 * - Drivers can call: Orchestrator.runFlowByName(project, Flows.REGISTRY, "FLOW_REQUEST_APPROVE", props)
 *
 * Assumptions / notes:
 * - The TestCases listed here MUST exist in the specified suite and will run in list order (top→bottom).
 * - Disabled TestCases are skipped by SoapUI. A failing case typically stops the run (depending on driver).
 * - This registry supports ONE suite per flow. If a flow needs cases across multiple suites,
 *   either move those cases into one suite or extend the orchestrator/registry shape.
 * - If you need a token step (e.g., GetCASToken) before every flow, either:
 *     a) put it as the FIRST item in the cases list, or
 *     b) let your Orchestrator handle token refresh centrally (cleaner).
 *
 * How to add a new flow (copy/paste pattern):
 *   MY_NEW_FLOW: [
 *     suite : 'My Suite Name',
 *     cases : ['Case A', 'Case B', 'Case C']
 *   ]
 *
 * Example usage in a driver:
 *   def props = [userId:'u1', amount:'10.00'] // values exposed at Project/Suite/Case scope by the orchestrator
 *   def results = Orchestrator.runFlowByName(project, Flows.REGISTRY, 'FLOW_REQUEST_DENY', props, true)
 *   log.info results.toString()
 */

class Flows {
  // Central registry of flows.
  // Key = flow name you’ll reference from drivers.
  // Value = [suite:'<Suite Name>', cases:['<TestCase1>', '<TestCase2>', ...]] in the order to run.
  static final Map<String, Map> REGISTRY = [

    // // Flow 1: request -> approve
    // FLOW_REQUEST_APPROVE: [
    //   suite : 'USS APIs',                 // suite where these TestCases live
    //   cases : ['Request AA', 'Approve AA'] // run 'Request AA' then 'Approve AA'
    // ],

    // // Flow 2: request -> deny (adds a Part 107 creation step at the end)
    // FLOW_REQUEST_DENY: [
    //   suite : 'USS APIs',
    //   cases : ['Request AA', 'Deny AA', 'Part 107 - Create Auto Authorization']
    // ],
    FLOW_REQUEST_APPROVE: [
      project: 'INT ENV PROJECT',
      suite  : 'USS APIs',
      tokenCase: 'Get Token',  
      cases  : ['Request AA','Part 107 - Create Auto Authorization']
    ],
    FLOW_REQUEST_TEST: [
      project: 'TEST ENV PROJECT',
      suite  : 'USS APIs',
      tokenCase: 'Get Token',  
      cases  : ['Request AA','Part 107 - Create Auto Authorization','EXCLUDE TC'],
      exclude: ['EXCLUDE TC']
    ],
    FLOW_INT_PROJECT: [
      project: 'INT ENV PROJECT',
      suite  : 'Errors',
      tokenCase: 'Get Token',  
      cases  : '*',
      exclude: ['Exclude Test Case']
    ],
    REST_PROJECT_1: [
      project: 'REST Project 1',
      suite  : 'Copy of REST1 Suite',
      tokenCase: 'Get Token',  
      cases  : '*',
      // exclude: ['Exclude Test Case']
    ],
  ]
}
