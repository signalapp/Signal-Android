/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.RemoteConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Internal user only notifier when "bad" things happen with group send endorsement sends.
 */
object GroupSendEndorsementInternalNotifier {

  private const val TAG = "GSENotifier"

  private var lastGroupSendNotify: Duration = 0.milliseconds
  private var skippedGroupSendNotifies = 0

  private var lastMissingNotify: Duration = 0.milliseconds

  @JvmStatic
  fun maybePostGroupSendFallbackError(context: Context) {
    if (!RemoteConfig.internalUser) {
      return
    }

    Log.internal().w(TAG, "Group send with GSE failed, GSE was likely out of date or incorrect", Throwable())

    val now = System.currentTimeMillis().milliseconds
    if (lastGroupSendNotify + 5.minutes > now && skippedGroupSendNotifies < 5) {
      skippedGroupSendNotifies++
      return
    }

    val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("[Internal-only] Group send failure (GSE)")
      .setContentText("Please tap to get a debug log")
      .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, SubmitDebugLogActivity::class.java), PendingIntentFlags.mutable()))
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.INTERNAL_ERROR, notification)

    lastGroupSendNotify = now
    skippedGroupSendNotifies = 0
  }

  @JvmStatic
  fun maybePostMissingGroupSendEndorsement(context: Context) {
    if (!RemoteConfig.internalUser) {
      return
    }

    Log.internal().w(TAG, "GSE missing for recipient", Throwable())

    val now = System.currentTimeMillis().milliseconds
    if (lastMissingNotify + 5.minutes > now) {
      return
    }

    val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("[Internal-only] Missing recipient (GSE)")
      .setContentText("Please tap to get a debug log")
      .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, SubmitDebugLogActivity::class.java), PendingIntentFlags.mutable()))
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.INTERNAL_ERROR, notification)

    lastMissingNotify = now
  }
}
