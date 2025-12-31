/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.spinner

import android.util.Log
import org.json.JSONObject
import org.signal.core.util.ExceptionUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class SpinnerLogItem(
  val level: Int,
  val time: Long,
  val thread: String,
  val tag: String,
  val message: String?,
  val throwable: Throwable?
) {
  fun serialize(): String {
    val stackTrace: String? = throwable?.let { ExceptionUtil.convertThrowableToString(throwable) }
    val formattedTime = dateFormat.format(Date(time))
    val paddedTag: String = when {
      tag.length > 23 -> tag.substring(0, 23)
      tag.length < 23 -> tag.padEnd(23)
      else -> tag
    }

    val levelString = when (level) {
      Log.VERBOSE -> "V"
      Log.DEBUG -> "D"
      Log.INFO -> "I"
      Log.WARN -> "W"
      Log.ERROR -> "E"
      else -> "?"
    }

    val out = JSONObject()
    out.put("level", levelString)
    out.put("time", formattedTime)
    out.put("thread", thread)
    out.put("tag", paddedTag)
    message?.let { out.put("message", it) }
    stackTrace?.let { out.put("stackTrace", it) }

    return out.toString(0)
  }

  companion object {
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
  }
}
