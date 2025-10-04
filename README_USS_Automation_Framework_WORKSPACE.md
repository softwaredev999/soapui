# USS_Automation_Framework (Workspace-Aware)

> Updated 2025-10-04 18:51

This setup assumes your **SoapUI workspace** has a top-level `lib/` and `test-data/` folder used by **all** projects, plus a folder for the automation project itself (`USS_Automation_Framework/`). Other SoapUI projects live alongside it in the workspace and are invoked via the **Flow Registry**.

---

## Workspace Layout

```
<workspace-root>/
├── lib/                         # shared Groovy libraries (one place)
├── test-data/                   # shared Excel inputs + output logs
│   ├── AA_Flow_Data.xlsx
│   ├── Part107_Data.xlsx
│   └── Results_Logs/
├── USS_Automation_Framework/    # this automation project folder
│   └── USS_Automation_Framework-soapui-project.xml
├── INT ENV PROJECT-soapui-project.xml
├── TEST ENV PROJECT-soapui-project.xml
└── (other projects).xml
```

**Notes**
- Keeping `lib/` and `test-data/` at the **workspace root** makes them reusable across multiple projects.
- The automation project (`USS_Automation_Framework`) **calls other projects** by names defined in `Flows.groovy`. Those projects **must exist** in the same workspace.

---

## Required Properties (Driver TestCase)

Define these **Custom Properties** on the **Driver TestCase** of `USS_Automation_Framework`:

| Property          | Value (example)                            | Purpose                                              |
|-------------------|---------------------------------------------|------------------------------------------------------|
| `DATA_FILES`      | `AA_Flow_Data.xlsx, Part107_Data.xlsx`      | CSV list of Excel files under `<workspace>/test-data` |
| `TS_01_DATA_FILE` | `AA_Flow_Data.xlsx`                         | Single file fallback for single-file driver          |
| `FLOW_DEFAULT`    | `FLOW_REQUEST_APPROVE`                      | Default flow if a row doesn’t specify one            |

> You may also add optional **Project Properties** to override directories:
> - `LIB_DIR`  (absolute or relative path to `lib/`)
> - `DATA_DIR` (absolute or relative path to `test-data/`)

If these are not set, the scripts **auto-discover** them: first try `<projectDir>/…`, then `<workspace-root>/…`.

---

## Library Roles (unchanged conceptually)
- **BootFlow.groovy** — Compiles & caches libraries once (context).
- **Lib.groovy** — Common helpers (Excel I/O, logging, random data).
- **Columns.groovy** — Central column names for Excel schema.
- **Flows.groovy** — Flow Registry (project/suite/case mapping).
- **OrchestratorFlow.groovy** — Calls external SoapUI projects & returns results.
- **ResultWriter.groovy** — Writes result rows + timestamps back to Excel.

---

## Execution Flow
1. **Driver** (e.g., `Driver_SingleOrMultiFile.groovy`) runs → calls **BootFlow**.
2. **BootFlow** finds **lib/** (Project prop → `<projectDir>` → `<workspace-root>`), compiles libs, caches classes in `context`.
3. Driver resolves **test-data/** (Project prop → `<projectDir>` → `<workspace-root>`).
4. For each Excel row: pick **flow** (or `FLOW_DEFAULT`) → **OrchestratorFlow** runs mapped project/suite/cases → capture assertions → write results.

---

## Flow Registry Reminder
Projects listed in `Flows.groovy` **must exist** in the workspace and their **suite/case names must match** exactly. Example:

```groovy
FLOW_REQUEST_APPROVE: [
  project  : 'TEST ENV PROJECT',
  suite    : 'USS APIs',
  tokenCase: 'Get Token',
  cases    : ['Request AA', 'Approve AA']
]
```

---

## Troubleshooting
- `${#TestCase#reNo}` prints literally → header mismatch in Excel; update `Columns.groovy` or your sheet header.
- “Project not found” → name typo or missing project in workspace.
- No rows executed → your Result filter or Excel path is wrong; verify `DATA_FILES`/`TS_01_DATA_FILE` and that files exist under `<workspace>/test-data`.
- Mixed logs → separate **Driver log**, **Script log**, **SoapUI log** by prefixing messages and writing result files to `test-data/Results_Logs`.

