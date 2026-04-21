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

  class TsvResult(
    private val columns: List<String>,
    private val rows: Iterator<List<String?>>
  ) : PluginResult("tsv") {
    fun toInputStream(): InputStream = object : InputStream() {
      private var buffer: ByteArray = columns.joinToString("\t").toByteArray()
      private var pos: Int = 0
      private var done: Boolean = false

      override fun read(): Int {
        if (!fill()) return -1
        return buffer[pos++].toInt() and 0xFF
      }

      override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (!fill()) return -1
        val toRead = minOf(buffer.size - pos, len)
        System.arraycopy(buffer, pos, b, off, toRead)
        pos += toRead
        return toRead
      }

      override fun close() {
        (rows as? AutoCloseable)?.close()
      }

      private fun fill(): Boolean {
        while (pos >= buffer.size) {
          if (done) return false
          if (rows.hasNext()) {
            buffer = ("\n" + rows.next().joinToString("\t") { it ?: "" }).toByteArray()
            pos = 0
          } else {
            done = true
            return false
          }
        }
        return true
      }
    }
  }

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
