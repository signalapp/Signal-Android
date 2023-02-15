package org.signal.spinner

sealed class PluginResult(val type: String) {
  data class TableResult(
    val columns: List<String>,
    val rows: List<List<String>>,
    val rowCount: Int = rows.size
  ) : PluginResult("table")
}
