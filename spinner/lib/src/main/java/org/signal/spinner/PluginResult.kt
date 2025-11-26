package org.signal.spinner

import java.io.InputStream

sealed class PluginResult(val type: String) {
  data class TableResult(
    val columns: List<String>,
    val rows: List<List<String>>,
    val rowCount: Int = rows.size
  ) : PluginResult("table")

  data class StringResult(
    val text: String
  ) : PluginResult("string")

  data class RawHtmlResult(
    val html: String
  ) : PluginResult("html")

  data class RawFileResult(
    val length: Long,
    val data: InputStream,
    val mimeType: String
  ) : PluginResult("file")
}
