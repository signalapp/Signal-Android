/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.apkupdate

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.ServiceUtil

object ApkUpdateNotifications {

  val TAG = Log.tag(ApkUpdateNotifications::class.java)

  /**
   * Shows a notification to prompt the user to install the app update. Only shown when silently auto-updating is not possible or are disabled by the user.
   * Note: This is an 'ongoing' notification (i.e. not-user dismissable) and never dismissed programatically. This is because the act of installing the APK
   * will dismiss it for us.
   */
  @SuppressLint("LaunchActivityFromNotification")
  fun showInstallPrompt(context: Context, downloadId: Long) {
    Log.d(TAG, "Showing install prompt. DownloadId: $downloadId")
    ServiceUtil.getNotificationManager(context).cancel(NotificationIds.APK_UPDATE_FAILED_INSTALL)

    val pendingIntent = PendingIntent.getBroadcast(
      context,
      1,
      Intent(context, ApkUpdateNotificationReceiver::class.java).apply {
        action = ApkUpdateNotificationReceiver.ACTION_INITIATE_INSTALL
        putExtra(ApkUpdateNotificationReceiver.EXTRA_DOWNLOAD_ID, downloadId)
      },
      PendingIntentFlags.updateCurrent()
    )

    val notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().APP_UPDATES)
      .setOngoing(true)
      .setContentTitle(context.getString(R.string.ApkUpdateNotifications_prompt_install_title))
      .setContentText(context.getString(R.string.ApkUpdateNotifications_prompt_install_body))
      .setSmallIcon(R.drawable.ic_notification)
      .setColor(ContextCompat.getColor(context, R.color.core_ultramarine))
      .setContentIntent(pendingIntent)
      .build()

    ServiceUtil.getNotificationManager(context).notify(NotificationIds.APK_UPDATE_PROMPT_INSTALL, notification)
  }

  fun dismissInstallPrompt(context: Context) {
    Log.d(TAG, "Dismissing install prompt.")
    ServiceUtil.getNotificationManager(context).cancel(NotificationIds.APK_UPDATE_PROMPT_INSTALL)
  }

  fun showInstallFailed(context: Context, reason: FailureReason) {
    Log.d(TAG, "Showing failed notification. Reason: $reason")
    ServiceUtil.getNotificationManager(context).cancel(NotificationIds.APK_UPDATE_PROMPT_INSTALL)

    val pendingIntent = PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java),
      PendingIntentFlags.immutable()
    )

    val notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().APP_UPDATES)
      .setContentTitle(context.getString(R.string.ApkUpdateNotifications_failed_general_title))
      .setContentText(context.getString(R.string.ApkUpdateNotifications_failed_general_body))
      .setSmallIcon(R.drawable.ic_notification)
      .setColor(ContextCompat.getColor(context, R.color.core_ultramarine))
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    ServiceUtil.getNotificationManager(context).notify(NotificationIds.APK_UPDATE_FAILED_INSTALL, notification)
  }

  fun showUpdateSuccess(context: Context, userInitiated: Boolean) {
    val pendingIntent = PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java),
      PendingIntentFlags.immutable()
    )

    val appVersionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName

    val body = if (userInitiated) {
      context.getString(R.string.ApkUpdateNotifications_manual_update_success_body, appVersionName)
    } else {
      context.getString(R.string.ApkUpdateNotifications_auto_update_success_body, appVersionName)
    }

    val notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().APP_UPDATES)
      .setContentTitle(context.getString(R.string.ApkUpdateNotifications_auto_update_success_title))
      .setContentText(body)
      .setSmallIcon(R.drawable.ic_notification)
      .setColor(ContextCompat.getColor(context, R.color.core_ultramarine))
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    ServiceUtil.getNotificationManager(context).notify(NotificationIds.APK_UPDATE_SUCCESSFUL_INSTALL, notification)
  }

  enum class FailureReason {
    UNKNOWN,
    ABORTED,
    BLOCKED,
    INCOMPATIBLE,
    INVALID,
    CONFLICT,
    STORAGE,
    TIMEOUT
  }
}
