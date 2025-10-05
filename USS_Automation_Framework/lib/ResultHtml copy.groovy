import groovy.json.JsonOutput
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat

class ResultHtml {
  File outFile
  StringBuilder buf = new StringBuilder(64_000)
  String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())

  ResultHtml(File outFile) {
    this.outFile = outFile
    start()
  }

  // ---------- init & finish ----------
  void start() {
    buf.append("""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<title>USS Automation Report</title>
<style>
  body{font:14px/1.4 system-ui,Segoe UI,Arial,sans-serif;margin:20px;background:#0b0e14;color:#e6edf3}
  .meta{margin-bottom:14px;opacity:.8}
  details{border:1px solid #2b3245;border-radius:10px;margin:10px 0;background:#0f1320}
  summary{cursor:pointer;padding:12px 14px;font-weight:600}
  .flow-head{display:flex;gap:10px;align-items:center}
  .badge{font:12px monospace;border-radius:999px;padding:2px 8px}
  .pass{background:#0f3a1a;color:#a7f3d0;border:1px solid #14532d}
  .fail{background:#3a1111;color:#fecaca;border:1px solid #7f1d1d}
  .grid{display:grid;grid-template-columns:1fr 1fr;gap:12px;padding:0 14px 14px}
  .card{background:#11162a;border:1px solid #26304a;border-radius:8px;padding:10px}
  .title{font-weight:600;margin-bottom:6px;opacity:.9}
  pre{white-space:pre-wrap;word-break:break-word;margin:0;max-height:420px;overflow:auto}
  .kv{display:grid;grid-template-columns:160px 1fr;gap:8px;padding:0 14px 10px}
  .sep{height:1px;background:#25314a;margin:10px 0}
  .tiny{font:12px;opacity:.8}
  .hdr{font-size:22px;margin:0 0 6px}
</style>
</head><body>
  <h1 class="hdr">USS Automation Report</h1>
  <div class="meta tiny">Generated: ${ts}</div>
  <div class="sep"></div>
""")
  }

  void finish() {
    buf.append("\n</body></html>")
    outFile.getParentFile().mkdirs()
    outFile.withWriter(StandardCharsets.UTF_8.name()) { it << buf.toString() }
  }

  // ---------- helpers ----------
  static String pretty(String maybeJson) {
    if (!maybeJson) return ''
    def txt = maybeJson.trim()
    try {
      if ((txt.startsWith('{') && txt.endsWith('}')) || (txt.startsWith('[') && txt.endsWith(']'))) {
        return JsonOutput.prettyPrint(txt)
      }
      return txt
    } catch (ignore) { return txt }
  }

  // ---------- sections ----------
  void addFlowStart(Map meta) {
    // meta: [row, flowName, status, refNo, dataFile]
    String badgeClass = (meta.status?.toString()?.toUpperCase() == 'PASS') ? 'pass' : 'fail'
    buf.append("""
<details open>
  <summary>
    <div class="flow-head">
      <span>${meta.row != null ? "#${meta.row} — " : ""}${meta.flowName ?: "UNNAMED FLOW"}</span>
      <span class="badge ${badgeClass}">${meta.status ?: "PENDING"}</span>
    </div>
  </summary>
  <div class="kv tiny">
    <div>Data file</div><div>${meta.dataFile ?: "-"}</div>
    <div>Ref No</div><div>${meta.refNo ?: "-"}</div>
  </div>
""")
  }

  void addStep(Map step) {
    // step: [name, request, response, status]
    String badgeClass = (step.status?.toString()?.toUpperCase() == 'PASS') ? 'pass' : 'fail'
    buf.append("""
  <div class="card">
    <div class="title">${step.name ?: "Step"} <span class="badge ${badgeClass}">${step.status ?: "-"}</span></div>
    <div class="grid">
      <div class="card">
        <div class="title tiny">Request</div>
        <pre>${pretty(step.request)}</pre>
      </div>
      <div class="card">
        <div class="title tiny">Response</div>
        <pre>${pretty(step.response)}</pre>
      </div>
    </div>
  </div>
""")
  }

  void addFlowEnd() {
    buf.append("</details>\n")
  }
}
