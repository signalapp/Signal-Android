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
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Internal user only notifier when "bad" things happen with group send endorsement sends.
 */
object GroupSendEndorsementInternalNotifier : SealedSenderAccess.FallbackListener {

  private const val TAG = "GSENotifier"

  private var lastGroupSendNotify: Duration = 0.milliseconds
  private var skippedGroupSendNotifies = 0

  private var lastMissingNotify: Duration = 0.milliseconds

  private var lastFallbackNotify: Duration = 0.milliseconds

  @JvmStatic
  fun init() {
    if (RemoteConfig.internalUser) {
      SealedSenderAccess.fallbackListener = this
    }
  }

  override fun onAccessToTokenFallback() {
    Log.w(TAG, "Fallback from access key to token", Throwable())
    postFallbackError(AppDependencies.application)
  }

  override fun onTokenToAccessFallback(hasAccessKeyFallback: Boolean) {
    Log.w(TAG, "Fallback from token hasAccessKey=$hasAccessKeyFallback", Throwable())
    postFallbackError(AppDependencies.application)
  }

  @JvmStatic
  fun postGroupSendFallbackError(context: Context) {
    val now = System.currentTimeMillis().milliseconds
    if (lastGroupSendNotify + 5.minutes > now && skippedGroupSendNotifies < 5) {
      skippedGroupSendNotifies++
      return
    }

    val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("[Internal-only] GSE failed for group send")
      .setContentText("Please tap to send a debug log")
      .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, SubmitDebugLogActivity::class.java), PendingIntentFlags.mutable()))
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.INTERNAL_ERROR, notification)

    lastGroupSendNotify = now
    skippedGroupSendNotifies = 0
  }

  @JvmStatic
  fun postMissingGroupSendEndorsement(context: Context) {
    val now = System.currentTimeMillis().milliseconds
    if (lastMissingNotify + 5.minutes > now) {
      return
    }

    val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("[Internal-only] GSE missing for recipient")
      .setContentText("Please tap to send a debug log")
      .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, SubmitDebugLogActivity::class.java), PendingIntentFlags.mutable()))
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.INTERNAL_ERROR, notification)

    lastMissingNotify = now
  }

  @JvmStatic
  fun postFallbackError(context: Context) {
    val now = System.currentTimeMillis().milliseconds
    if (lastFallbackNotify + 5.minutes > now) {
      return
    }

    val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("[Internal-only] GSE fallback occurred!")
      .setContentText("Please tap to send a debug log")
      .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, SubmitDebugLogActivity::class.java), PendingIntentFlags.mutable()))
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.INTERNAL_ERROR, notification)

    lastFallbackNotify = now
  }
}
