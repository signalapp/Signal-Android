/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.logsubmit

import android.app.Application
import android.content.Context
import org.thoughtcrime.securesms.database.LogDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Summarizes the recorded database issues (grouped by name) rather than listing every individual occurrence.
 */
class LogSectionDatabaseIssues : LogSection {
  override fun getTitle(): String = "APP ISSUES"

  override fun getContent(context: Context): CharSequence {
    val summaries = LogDatabase.getInstance(context.applicationContext as Application).issues.getSummary()

    if (summaries.isEmpty()) {
      return "None"
    }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    return summaries.joinToString(separator = "\n\n") { summary ->
      """
        -- ${summary.name}
        Count       : ${summary.count}
        Max Priority: ${summary.maxPriority.label}
        Avg Duration: ${summary.averageDuration?.let { "${it}ms" } ?: "n/a"}
        First Seen  : ${dateFormat.format(Date(summary.firstSeen))}
        Last Seen   : ${dateFormat.format(Date(summary.lastSeen))}
        Last Version: ${summary.lastVersion}
      """.trimIndent()
    }
  }

  override fun hasContent(): Boolean {
    return true
  }
}
