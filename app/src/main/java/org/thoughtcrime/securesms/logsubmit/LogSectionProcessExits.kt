/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.logsubmit

import android.content.Context
import androidx.annotation.RequiresApi
import org.thoughtcrime.securesms.database.LocalMetricsDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.ProcessExitReasonTracker
import org.thoughtcrime.securesms.util.SignalLocalMetrics

/**
 * Recent process exits as reported by the system, plus the longer history of force stops kept in local metrics.
 */
@RequiresApi(30)
class LogSectionProcessExits : LogSection {

  companion object {
    private const val MAX_FORCE_STOPS = 50
  }

  override fun getTitle(): String = "PROCESS EXITS"

  override fun getContent(context: Context): CharSequence {
    val exits = ProcessExitReasonTracker.getExits(context)
    val forceStops = LocalMetricsDatabase.getInstance(AppDependencies.application).getEventTimes(SignalLocalMetrics.ProcessExit.FORCE_STOP_EVENT, MAX_FORCE_STOPS)

    return buildString {
      append("-- Recent exits (from the system)\n")
      if (exits.isEmpty()) {
        append("None\n")
      } else {
        exits.forEach { appendLine(ProcessExitReasonTracker.describe(it)) }
      }

      append("\n-- Force stops in the last week (newest first, at most $MAX_FORCE_STOPS)\n")
      if (forceStops.isEmpty()) {
        append("None\n")
      } else {
        forceStops.forEach { appendLine(ProcessExitReasonTracker.formatTimestamp(it)) }
      }
    }
  }
}
