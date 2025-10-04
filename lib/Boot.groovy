// // lib/Boot.groovy
// def dir = context.expand('${#Project#LIB_DIR}') ?: (context.expand('${projectDir}') + '/lib')
// def f   = new File(dir, 'Lib.groovy')
// def c   = context['Lib']
// if (!(c instanceof Class)) {
//   c = new GroovyClassLoader(this.class.classLoader).parseClass(f)
//   context['Lib'] = c
// }
// return null
// lib/Boot.groovy


// def libDir = context.expand('${#Project#LIB_DIR}') ?: (context.expand('${projectDir}') + '/lib')
// def loader = new GroovyClassLoader(this.class.classLoader)

// ['Lib.groovy', 'Orchestrator.groovy'].each { fname ->
//   def f = new File(libDir, fname)
//   if (!f.exists()) throw new FileNotFoundException("Missing: ${f.absolutePath}")
//   def c = context[fname]
//   if (!(c instanceof Class)) {
//     c = loader.parseClass(f)
//     context[fname] = c
//   }
// }
// // expose short aliases
// context['Lib']          = context['Lib.groovy']
// context['Orchestrator'] = context['Orchestrator.groovy']

// lib/Boot.groovy
def libDir = context.expand('${#Project#LIB_DIR}') ?: (context.expand('${projectDir}') + '/lib')
def loader = new GroovyClassLoader(this.class.classLoader)

['Lib.groovy', 'Orchestrator.groovy', 'Columns.groovy'].each { fname ->
  def f = new File(libDir, fname)
  if (!f.exists()) throw new FileNotFoundException("Missing: ${f.absolutePath}")
  def c = context[fname]
  if (!(c instanceof Class)) {
    c = loader.parseClass(f)
    context[fname] = c
  }
}

// short aliases
context['Lib']          = context['Lib.groovy']
context['Orchestrator'] = context['Orchestrator.groovy']
context['Columns']      = context['Columns.groovy']


