/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.ServiceUtil
import kotlin.time.Duration.Companion.days

/**
 * Notifies users that their build expired and redirects to the download page on click.
 */
class DeprecatedNotificationJob private constructor(parameters: Parameters) : Job(parameters) {
  companion object {
    const val KEY: String = "DeprecatedNotificationJob"
    private val TAG = Log.tag(DeprecatedNotificationJob::class.java)

    @JvmStatic
    fun enqueue() {
      AppDependencies.jobManager.add(DeprecatedNotificationJob())
    }
  }

  private constructor() : this(
    Parameters.Builder()
      .setQueue("DeprecatedNotificationJob")
      .setLifespan(7.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    if (NotificationChannels.getInstance().areNotificationsEnabled()) {
      val intent: Intent

      if (BuildConfig.MANAGES_APP_UPDATES) {
        Log.d(TAG, "Showing deprecated notification for website APK")
        intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.org/android/apk"))
      } else {
        Log.d(TAG, "Showing deprecated notification for PlayStore")
        val packageName = context.packageName
        intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
      }

      val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
      val builder = NotificationCompat.Builder(context, NotificationChannels.getInstance().APP_ALERTS)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.DeprecatedNotificationJob_update_signal))
        .setContentText(context.getString(R.string.DeprecatedNotificationJob_this_version_of_signal_has_expired))
        .setContentIntent(pendingIntent)

      ServiceUtil.getNotificationManager(context).notify(NotificationIds.APK_UPDATE_PROMPT_INSTALL, builder.build())
    }

    return Result.success()
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<DeprecatedNotificationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): DeprecatedNotificationJob {
      return DeprecatedNotificationJob(parameters)
    }
  }
}
