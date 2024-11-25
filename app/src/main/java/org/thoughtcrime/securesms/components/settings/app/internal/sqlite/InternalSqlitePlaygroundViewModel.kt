/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.sqlite

import android.database.Cursor
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.signal.core.util.ExceptionUtil
import org.signal.core.util.readToList
import org.signal.core.util.roundedString
import org.thoughtcrime.securesms.database.SignalDatabase
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

class InternalSqlitePlaygroundViewModel : ViewModel() {

  private val _queryResults: MutableState<QueryResult?> = mutableStateOf(null)
  val queryResults: State<QueryResult?>
    get() = _queryResults

  fun onQuerySubmitted(query: String) {
    viewModelScope.launch(Dispatchers.IO) {
      _queryResults.value = null

      val startTime = System.nanoTime()
      try {
        SignalDatabase.rawDatabase.rawQuery(query).use { cursor ->
          val columnNames = cursor.columnNames.toList()
          val rows: List<List<Any?>> = cursor.readToList { row ->
            val out: MutableList<Any?> = ArrayList(row.columnCount)
            for (i in 0 until row.columnCount) {
              if (row.getType(i) == Cursor.FIELD_TYPE_BLOB) {
                out.add(row.getBlob(i))
              } else {
                out.add(row.getString(i))
              }
            }
            out
          }

          val endTime = System.nanoTime()
          val timeMs: String = (endTime - startTime).nanoseconds.toDouble(DurationUnit.MILLISECONDS).roundedString(2)
          _queryResults.value = QueryResult(columns = columnNames, rows = rows, totalTimeString = timeMs)
        }
      } catch (e: Exception) {
        _queryResults.value = QueryResult(
          columns = listOf("Query failed!"),
          rows = listOf(listOf(ExceptionUtil.convertThrowableToString(e))),
          totalTimeString = ""
        )
      }
    }
  }

  data class QueryResult(
    val columns: List<String>,
    val rows: List<List<Any?>>,
    val totalTimeString: String
  )
}
