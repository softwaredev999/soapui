/**
 * Boot.groovy (multi-file loader)
 *
 * What this does:
 * - Figures out where my library folder is (Project property LIB_DIR, or fallback to ${projectDir}/lib).
 * - Compiles a small set of Groovy class files (Lib.groovy, OrchestratorFlow.groovy, Columns.groovy, Flows.groovy).
 * - Caches each compiled Class in SoapUI's `context` so they are loaded only once per run.
 * - Adds short, friendly aliases in `context` (e.g., context['Lib']) so drivers can call them easily.
 *
 * How to use:
 * - At the very top of a Groovy driver step (or TestCase/Suite Setup), do:
 *     evaluate(new File(context.expand('${projectDir}') + '/lib/Boot.groovy'))
 * - Then in the same step you can call:
 *     def Lib = context['Lib']
 *     Lib.ping(this, 'Driver - Demo')
 *
 * Notes:
 * - This script does not change logic; only compiles and exposes classes.
 * - If a file is missing, it throws a clear FileNotFoundException.
 * - Saved libs should be UTF-8 without BOM to avoid Groovy “unexpected character” errors.
 */

def libDir = context.expand('${#Project#LIB_DIR}') ?: (context.expand('${projectDir}') + '/lib') // library folder: project prop or fallback
def loader = new GroovyClassLoader(this.class.classLoader)                                       // classloader used to compile .groovy files

// List the library source files we expect, and compile each one only if not already in context cache
['Lib.groovy','OrchestratorFlow.groovy','Columns.groovy','Flows.groovy'].each { fname ->
  def f = new File(libDir, fname)                                                                 // full path to lib file
  if (!f.exists()) throw new FileNotFoundException("Missing: ${f.absolutePath}")                  // fail fast with a clear message

  def c = context[fname]                                                                          // cached Class under filename key?
  if (!(c instanceof Class)) {                                                                    // if not compiled yet this run
    c = loader.parseClass(f)                                                                      // compile .groovy into a Class
    context[fname] = c                                                                            // cache it by filename
  }
}

// Add short aliases so drivers write context['Lib'] vs context['Lib.groovy']
context['Lib']          = context['Lib.groovy']
context['Orchestrator'] = context['OrchestratorFlow.groovy']
context['Columns']      = context['Columns.groovy']
context['Flows']        = context['Flows.groovy']
