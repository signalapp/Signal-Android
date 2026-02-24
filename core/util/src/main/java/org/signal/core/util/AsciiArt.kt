package org.signal.core.util

import android.database.Cursor
import kotlin.math.max

class AsciiArt {

  private class Table(
    private val columns: List<String>,
    private val rows: List<List<String>>
  ) {
    override fun toString(): String {
      val columnWidths = columns.map { column -> column.length }.toIntArray()

      rows.forEach { row: List<String> ->
        columnWidths.forEachIndexed { index, currentMax ->
          columnWidths[index] = max(row[index].length, currentMax)
        }
      }

      val builder = StringBuilder()

      columns.forEachIndexed { index, column ->
        builder.append(COLUMN_DIVIDER).append(" ").append(rightPad(column, columnWidths[index])).append(" ")
      }
      builder.append(COLUMN_DIVIDER)

      builder.append("\n")

      columnWidths.forEach { width ->
        builder.append(COLUMN_DIVIDER)
        builder.append(ROW_DIVIDER.repeat(width + 2))
      }
      builder.append(COLUMN_DIVIDER)

      builder.append("\n")

      rows.forEach { row ->
        row.forEachIndexed { index, column ->
          builder.append(COLUMN_DIVIDER).append(" ").append(rightPad(column, columnWidths[index])).append(" ")
        }
        builder.append(COLUMN_DIVIDER)
        builder.append("\n")
      }

      return builder.toString()
    }
  }

  companion object {
    private const val COLUMN_DIVIDER = "|"
    private const val ROW_DIVIDER = "-"

    /**
     * Will return a string representing a table of the provided cursor. The caller is responsible for the lifecycle of the cursor.
     */
    @JvmStatic
    fun tableFor(cursor: Cursor): String {
      val columns: MutableList<String> = mutableListOf()
      val rows: MutableList<List<String>> = mutableListOf()

      columns.addAll(cursor.columnNames)

      while (cursor.moveToNext()) {
        val row: MutableList<String> = mutableListOf()

        for (i in 0 until columns.size) {
          row += cursor.getString(i)
        }

        rows += row
      }

      return Table(columns, rows).toString()
    }

    private fun rightPad(value: String, length: Int): String {
      if (value.length >= length) {
        return value
      }
      val out = java.lang.StringBuilder(value)
      while (out.length < length) {
        out.append(" ")
      }
      return out.toString()
    }
  }
}
