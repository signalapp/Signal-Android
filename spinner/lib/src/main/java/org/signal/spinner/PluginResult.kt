package org.signal.spinner

import fi.iki.elonen.NanoHTTPD.Response
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

  data class JsonResult(
    val json: String
  ) : PluginResult("json")

  data class ErrorResult(
    val status: Response.Status = Response.Status.INTERNAL_ERROR,
    val message: String
  ) : PluginResult("error") {
    companion object {
      fun notFound(message: String): PluginResult.ErrorResult {
        return ErrorResult(status = Response.Status.NOT_FOUND, message = message)
      }
    }
  }
}
