# USS_Automation_Framework

## 📘 Overview
`USS_Automation_Framework` is a central SoapUI automation project designed to orchestrate multiple SoapUI projects using a Flow Registry and Excel-based test data. It handles Request → Approve → Cancel workflows, logging Pass/Fail results automatically.

---

## 🧱 1. Installation Steps

1. **Install SoapUI (Recommended 5.7.0+)**
   - Download: https://www.soapui.org/downloads/soapui
   - Follow installation wizard and verify Groovy scripting.

2. **Verify Groovy**
   ```groovy
   log.info "Groovy works fine!"
   ```

3. **Create Workspace**
   ```
   C:\SoapUI_Automation\USS_Automation_Framework\
   ```

4. **Place Files**
   Copy `lib/` and `test-data/` folders into project directory.

---

## 📂 2. Folder Structure

```
USS_Automation_Framework/
├── USS_Automation_Framework.xml
├── lib/
│   ├── BootFlow.groovy
│   ├── Lib.groovy
│   ├── Columns.groovy
│   ├── Flows.groovy
│   ├── OrchestratorFlow.groovy
│   └── ResultWriter.groovy
├── test-data/
│   ├── TS_01_DATA_FILE.xlsx
│   ├── AA_Flow_Data.xlsx
│   ├── Part107_Data.xlsx
│   └── Results_Logs/
└── drivers/
    ├── Driver_MultiFile.groovy
    ├── Driver_SingleFile.groovy
    └── Driver_SingleFlow.groovy
```

---

## ⚙️ 3. Library Responsibilities

| Library | Description |
|----------|-------------|
| BootFlow.groovy | Loads and caches Lib, Columns, Flows, and Orchestrator classes. |
| Lib.groovy | Reusable helpers (Excel, logging, data generation). |
| Columns.groovy | Defines Excel column names. |
| Flows.groovy | Registry of all project flows. |
| OrchestratorFlow.groovy | Executes external SoapUI projects. |
| ResultWriter.groovy | Writes results to Excel. |

---

## 🔗 4. How Libraries and Data Files Work Together

1. Driver script calls BootFlow.groovy → loads libraries.
2. Reads Excel file defined in DATA_FILES or TS_01_DATA_FILE.
3. Iterates rows → executes flows defined in Flows.groovy.
4. OrchestratorFlow runs target SoapUI projects and logs results.

---

## 🏃‍♂️ 5. What Happens After Running

1. Load libraries → read Excel → run flows.
2. For each flow, open external project and execute test cases.
3. Write results (PASS/FAIL) and timestamps back to Excel.
4. Generate result log in `/test-data/Results_Logs/`.

---

## 📄 6. Required Project Properties

| Property | Description | Example |
|-----------|-------------|----------|
| DATA_FILES | Comma-separated Excel files | `AA_Flow_Data.xlsx, Part107_Data.xlsx` |
| TS_01_DATA_FILE | Single Excel file name | `TS_01_DATA_FILE.xlsx` |

Add these as Custom Properties under `Driver_RunAllFlows` Test Case.

---

## 🧭 7. Prerequisite Projects

Projects listed in `Flows.groovy` must exist in the **same SoapUI workspace** with matching suite and case names.

```groovy
FLOW_REQUEST_TEST: [
  project: 'USS APIs',
  suite  : 'AA Flows',
  tokenCase: 'Get Token',
  cases  : ['Request AA', 'Approve AA', 'Cancel AA']
]
```

---

## 🧩 8. Execution Examples

**Single File Run**
```groovy
def fileName = context.expand('{#TestCase#TS_01_DATA_FILE}')
Lib.runSingleFile(fileName)
```

**Multiple File Run**
```groovy
def fileList = context.expand('{#TestCase#DATA_FILES}')
Lib.runMultiFile(fileList)
```

---

## ✅ 9. Expected Output

- Excel updated with PASS/FAIL results.
- Timestamped result file created in `/test-data/Results_Logs/`.
- Console summary:
  ```
  ===== Execution Summary =====
  Total Rows: 10
  Passed: 8
  Failed: 2
  =============================
  ```

---

## 🧰 10. Troubleshooting

| Issue | Cause | Fix |
|--------|--------|-----|
| `${#TestCase#reNo}` shows literally | Header renamed, update Columns.groovy |
| Project not found | Missing in workspace |
| No rows executed | “Result” column not “DID NOT RUN” |
| Blank response | Assertion missing in target test |

---

## 🧠 Author Notes

- Modular flow definitions = no driver changes.
- Keep Excel headers in sync with Columns.groovy.
- Maintain consistent naming across all projects.

---

### 📅 Version 2025-10-04
