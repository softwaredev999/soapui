/**
 * Columns.groovy
 *
 * What this file is for:
 * - One central place to define the **exact header names** used in your spreadsheets/CSV.
 * - Drivers and readers can refer to `Columns.NAMES.XYZ` instead of hard-typing strings everywhere.
 * - If a header changes in Excel, update it **here once** and all scripts stay in sync.
 *
 * How it’s used:
 * - When reading rows, you’ll access values like: `row[Columns.NAMES.ID]`, `row[Columns.NAMES.EMAIL]`.
 * - When writing back to Excel, use `Columns.NAMES.ALT` to locate the “Max Alt” column.
 *
 * Notes:
 * - Keys on the left (ID, NAME, …) are your stable code constants.
 * - Strings on the right are the **literal header text** in the sheet.
 * - Do not change the left-hand keys without updating code that references them.
 */

class Columns {
  // Map of constant -> actual header text in the spreadsheet
  static final Map<String,String> NAMES = [
    ID    : "id",
    NAME  : "name",
    EMAIL : "email",
    ALT   : "Max Alt",
    FLOW  : "flow"   // a column in Excel that picks the flow per data row
  ]
}
