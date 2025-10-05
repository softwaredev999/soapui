/**
 * BootFlow.groovy  — strict project-lib loader
 * Loads Lib/Columns/Flows/Orchestrator(+ResultHtml) from:
 *   <projectDir>\lib  (ONLY — no workspace fallback)
 * Caches: context['Lib'], ['Columns'], ['Flows'], ['Orchestrator'], ['ResultHtml']
 * Marks:  context['__LIB_DIR__'], context['__DATA_DIR__'], context['__LIB_SRC_*__']
 * Force reload: set TestCase/Project prop BOOT_FORCE_RELOAD = true
 */
import java.security.MessageDigest

def md5Of = { File f -> MessageDigest.getInstance('MD5').digest(f.bytes).encodeHex().toString() }

def projDir = new File(context.expand('${projectDir}'))
File libDir  = new File(projDir, 'lib')
File dataDir = new File(projDir, 'test-data')

// --- STRICT: must exist under project ---
assert libDir.exists()  : "BootFlow: Project lib folder not found: ${libDir.absolutePath}"
if (!dataDir.exists())  dataDir.mkdirs()

boolean forceReload = ((context.testCase?.getPropertyValue('BOOT_FORCE_RELOAD')
                     ?: context.project?.getPropertyValue('BOOT_FORCE_RELOAD')
                     ?: 'false').toString().equalsIgnoreCase('true'))

boolean libChanged = (context['__LIB_DIR__'] as String) != libDir.absolutePath
boolean missing    = !((context['Lib'] instanceof Class) &&
                       (context['Columns'] instanceof Class) &&
                       (context['Flows'] instanceof Class) &&
                       (context['Orchestrator'] instanceof Class))

if (forceReload || libChanged || missing) {
  def gcl = new GroovyClassLoader(this.class.classLoader)
  def compile = { String fileName ->
    File f = new File(libDir, fileName)
    assert f.exists() : "BootFlow: Missing library: ${f.absolutePath}"
    def cls = gcl.parseClass(f.text, fileName)
    def key = fileName.replace('.groovy','')
    context["__LIB_SRC_${key}__"] = f.absolutePath
    context["__LIB_MD5_${key}__"] = md5Of(f)
    cls
  }

  context['Lib']          = compile('Lib.groovy')
  context['Columns']      = compile('Columns.groovy')
  context['Flows']        = compile('Flows.groovy')
  context['Orchestrator'] = compile('OrchestratorFlow.groovy')
  if (new File(libDir, 'ResultHtml.groovy').exists()) {
    context['ResultHtml'] = compile('ResultHtml.groovy')
  }

  context['__LIB_DIR__']  = libDir.absolutePath
  context['__DATA_DIR__'] = dataDir.absolutePath

  log.info "[BootFlow] Reloaded from PROJECT lib: ${libDir.absolutePath}"
} else {
  log.info "[BootFlow] Using cached libs from: ${context['__LIB_DIR__']}"
}
