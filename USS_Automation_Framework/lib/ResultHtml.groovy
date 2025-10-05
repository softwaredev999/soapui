// lib/ResultHtml.groovy
// Simple, dependency-free HTML report builder for SoapUI drivers.

class ResultHtml {
  final File outFile
  private final StringBuilder body = new StringBuilder(128 * 1024)
  private final List<Map> flows = []
  private long startMs = System.currentTimeMillis()
  private int idSeq = 0

  ResultHtml(File outFile) {
    this.outFile = outFile
  }

  // ---- Public API (used by the driver) --------------------------------------

  /** Start a flow section (one Excel row). Keys: row, flowName, status, refNo, dataFile */
  void addFlowStart(Map m) {
    def status = (m.status ?: 'UNKNOWN').toString().toUpperCase()
    def openAttr = status == 'FAIL' ? ' open' : ''
    int id = (++idSeq)
    m.id = id
    flows << m

    body.append("""
<section id="flow-${id}" class="flow">
  <details${openAttr}>
    <summary>
      <span class="badge ${cssStatus(status)}">${safe(status)}</span>
      <span class="sum-text">Row ${safe(m.row?.toString() ?: '?')} • ${safe(m.flowName ?: '—')}</span>
      <span class="meta">
        ${m.refNo ? "Ref: ${safe(m.refNo.toString())} • " : ""}
        ${m.dataFile ? safe(m.dataFile.toString()) : ""}
      </span>
    </summary>
""")
  }

  /** Add a request/response step inside the current flow. Keys: name, request, response, status */
  void addStep(Map s) {
    def nm = safe(s.name ?: 'Step')
    def req = mono(s.request)
    def resp = mono(s.response)
    def st = cssStatus((s.status ?: 'UNKNOWN').toString().toUpperCase())

    body.append("""
    <div class="step-card ${st}">
      <div class="step-title">${nm}</div>
      <div class="step-grid">
        <div>
          <div class="label">Request</div>
          <pre class="code">${req}</pre>
        </div>
        <div>
          <div class="label">Response</div>
          <pre class="code">${resp}</pre>
        </div>
      </div>
    </div>
""")
  }

  /** End current flow details */
  void addFlowEnd() {
    body.append("""
  </details>
</section>
""")
  }

  /** Finalize and write the file */
  void finish() {
    int pass = flows.count { (it.status ?: '').toString().equalsIgnoreCase('PASS') }
    int fail = flows.size() - pass
    String when = new Date(startMs).format("yyyy-MM-dd HH:mm:ss")
    String dur  = fmtDuration(System.currentTimeMillis() - startMs)

    StringBuilder html = new StringBuilder(body.length() + 64 * 1024)
    html.append("""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>USS Automation Report</title>
<style>
  :root {
    --bg:#0b1020; --card:#121a33; --muted:#8fa3bf; --ok:#16a34a; --bad:#dc2626; --unk:#a3a3a3;
    --ink:#e6ecff; --ink2:#c7d2fe; --link:#8ab4ff; --chip:#182446; --code:#0a1228;
    --ring:#203055;
  }
  *{box-sizing:border-box} html,body{margin:0;padding:0;background:var(--bg);color:var(--ink);font:14px/1.45 ui-sans-serif,system-ui,Segoe UI,Roboto,Helvetica,Arial}
  a{color:var(--link);text-decoration:none} a:hover{text-decoration:underline}
  header{
    position:sticky;top:0;z-index:5;background:linear-gradient(180deg,rgba(10,18,40,.95),rgba(10,18,40,.75));
    backdrop-filter:saturate(120%) blur(6px); border-bottom:1px solid var(--ring);
  }
  .wrap{max-width:1100px;margin:0 auto;padding:14px 18px}
  .h-row{display:flex;gap:14px;align-items:center;flex-wrap:wrap}
  .title{font-weight:700;font-size:18px;letter-spacing:.3px}
  .spacer{flex:1}
  .kpi{display:inline-flex;align-items:center;gap:8px;background:var(--chip);padding:6px 10px;border-radius:999px;border:1px solid var(--ring)}
  .dot{width:8px;height:8px;border-radius:999px;display:inline-block}
  .ok{background:var(--ok)} .bad{background:var(--bad)} .unk{background:var(--unk)}
  button{
    background:var(--chip);color:var(--ink);border:1px solid var(--ring);
    padding:6px 10px;border-radius:10px;cursor:pointer
  }
  main{max-width:1100px;margin:18px auto;padding:0 18px 40px}
  nav.toc{margin:16px 0 8px}
  nav.toc ul{list-style:none;margin:0;padding:0;display:grid;gap:8px}
  nav.toc li a{display:flex;gap:8px;align-items:center;background:var(--card);padding:8px 10px;border-radius:10px;border:1px solid var(--ring)}
  .badge{
    display:inline-flex;align-items:center;gap:6px;padding:3px 8px;border-radius:999px;font-size:12px;
    border:1px solid var(--ring);background:var(--chip);color:var(--ink2)
  }
  .badge.pass{background:rgba(22,163,74,.15);color:#a7f3d0;border-color:rgba(22,163,74,.35)}
  .badge.fail{background:rgba(220,38,38,.15);color:#fecaca;border-color:rgba(220,38,38,.35)}
  .badge.unknown{background:rgba(163,163,163,.12);color:#e5e7eb;border-color:rgba(163,163,163,.35)}
  section.flow{margin:14px 0}
  details{background:var(--card);border:1px solid var(--ring);border-radius:14px;overflow:hidden}
  summary{display:flex;gap:12px;align-items:center;cursor:pointer;padding:12px 14px}
  summary .sum-text{font-weight:600}
  summary .meta{margin-left:auto;color:var(--muted);font-size:12px}
  .step-card{border-top:1px solid var(--ring);padding:12px}
  .step-card .step-title{font-weight:600;margin-bottom:10px}
  .step-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}
  @media (max-width:900px){ .step-grid{grid-template-columns:1fr} }
  .label{color:var(--muted);font-size:12px;margin-bottom:4px}
  pre.code{
    background:var(--code);border:1px solid var(--ring);border-radius:10px;padding:10px;
    max-height:400px;overflow:auto;white-space:pre-wrap;word-break:break-word
  }
  footer{max-width:1100px;margin:24px auto 60px;padding:0 18px;color:var(--muted)}
</style>
<script>
  function toggleAll(open){
    document.querySelectorAll('details').forEach(d => d.open = !!open);
  }
  function scrollTopSmooth(){ window.scrollTo({top:0,behavior:'smooth'}); }
</script>
</head>
<body>
  <header>
    <div class="wrap h-row">
      <div class="title">USS Automation Report</div>
      <span class="kpi"><span class="dot ok"></span><b>${pass}</b> passed</span>
      <span class="kpi"><span class="dot bad"></span><b>${fail}</b> failed</span>
      <span class="kpi"><span class="dot unk"></span><b>${flows.size()}</b> total</span>
      <span class="spacer"></span>
      <button onclick="toggleAll(true)">Expand all</button>
      <button onclick="toggleAll(false)">Collapse all</button>
      <button onclick="scrollTopSmooth()">Top</button>
    </div>
    <div class="wrap" style="padding-top:0;padding-bottom:12px;color:var(--muted);font-size:12px">
      Generated: ${when} • Duration: ${dur}
    </div>
  </header>

  <main>
    <nav class="toc">
      <ul>
""")

    // TOC entries
    flows.each { m ->
      html.append("""        <li>
          <a href="#flow-${m.id}">
            <span class="badge ${cssStatus((m.status ?: 'UNKNOWN').toString().toUpperCase())}">
              ${safe((m.status ?: 'UNKNOWN').toString().toUpperCase())}
            </span>
            <span>Row ${safe(m.row?.toString() ?: '?')} • ${safe(m.flowName ?: '—')}</span>
            <span style="margin-left:auto;color:var(--muted);font-size:12px">
              ${m.dataFile ? safe(m.dataFile.toString()) : ''}
            </span>
          </a>
        </li>
""")
    }

    html.append("""      </ul>
    </nav>

${body.toString()}

  </main>
  <footer>
    <div>File: ${safe(outFile.name)} • Location: ${safe(outFile.parent ?: '')}</div>
  </footer>
</body>
</html>
""")

    outFile.parentFile?.mkdirs()
    outFile.setText(html.toString(), 'UTF-8')
  }

  // ---- Helpers ---------------------------------------------------------------

  private static String cssStatus(String s) {
    switch ((s ?: '').toUpperCase()) {
      case 'PASS': return 'pass'
      case 'FAIL': return 'fail'
      default:     return 'unknown'
    }
  }

  private static String fmtDuration(long ms) {
    long s = (long)Math.floor(ms / 1000.0)
    long h = s.intdiv(3600); s -= h * 3600
    long m = s.intdiv(60);   s -= m * 60
    return String.format("%02dh:%02dm:%02ds", h, m, s)
  }

  /** Escape HTML + normalize to avoid broken markup. */
  private static String safe(String s) {
    if (s == null) return ''
    return s
      .replace('&','&amp;')
      .replace('<','&lt;')
      .replace('>','&gt;')
      .replace('"','&quot;')
      .replace("'",'&#39;')
  }

  /** Monospace-escape + trim giant blobs defensively (still safe for big JSON). */
  private static String mono(Object o) {
    String s = o == null ? '' : String.valueOf(o)
    // do not pretty-print here; driver already pretty-prints
    int limit = 200_000 // ~200 KB per pane to keep the HTML snappy
    if (s.length() > limit) s = s.substring(0, limit) + "\\n...[truncated]"
    return safe(s)
  }
}
