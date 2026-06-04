/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.signal.core.util.PendingIntentFlags
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.RemoteConfig
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Notifier that surfaces SQLite write-lock contention. Gated behind the [RemoteConfig.slowDatabaseNotifications] flag.
 */
object SlowTransactionInternalNotifier {

  private const val THRESHOLD = 5

  private val NOTIFY_INTERVAL = 30.minutes

  private val IGNORED_STACK_TRACE_CLASSES = listOf(
    "BackupRepository",
    "BackupMessagesJob",
    "ArchiveAttachmentReconciliationJob",
    "SubmitDebugLogRepository"
  )

  private val count = AtomicInteger(0)

  @Volatile
  private var lastNotify: Duration = 0.milliseconds

  @JvmStatic
  fun onSlowEvent() {
    if (!RemoteConfig.slowDatabaseNotifications) {
      return
    }

    if (isExpectedSlowOperation()) {
      return
    }

    if (count.incrementAndGet() < THRESHOLD) {
      return
    }

    val now = System.currentTimeMillis().milliseconds
    if (lastNotify + NOTIFY_INTERVAL > now) {
      return
    }

    val context = AppDependencies.application
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      return
    }

    val observed = count.getAndSet(0)
    lastNotify = now

    val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("[Internal-only] Slow database activity")
      .setContentText("$observed slow database operations (transactions/queries) observed. Please tap to get a debug log.")
      .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, SubmitDebugLogActivity::class.java), PendingIntentFlags.mutable()))
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.INTERNAL_ERROR, notification)
  }

  private fun isExpectedSlowOperation(): Boolean {
    return Thread
      .currentThread()
      .stackTrace
      .any { element ->
        IGNORED_STACK_TRACE_CLASSES.any {
          element.className.contains(it)
        }
      }
  }
}
