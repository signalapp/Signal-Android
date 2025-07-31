/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import java.util.concurrent.locks.ReentrantLock
import javax.annotation.CheckReturnValue
import kotlin.concurrent.withLock

/**
 * Foreground service to provide "long" run support to backup jobs.
 */
class BackupProgressService : SafeForegroundService() {

  companion object {
    private val TAG = Log.tag(BackupProgressService::class)

    @SuppressLint("StaticFieldLeak")
    private var controller: Controller? = null
    private val controllerLock = ReentrantLock()

    private var title: String = ""
    private var progress: Float = 0f
    private var indeterminate: Boolean = true

    @CheckReturnValue
    fun start(context: Context, startingTitle: String): Controller {
      controllerLock.withLock {
        if (controller != null) {
          Log.w(TAG, "Starting service with existing controller")
        }

        controller = Controller(context, startingTitle)
        val started = SafeForegroundService.start(context, BackupProgressService::class.java)
        if (started) {
          Log.i(TAG, "[start] Starting")
        } else {
          Log.w(TAG, "[start] Service already started")
        }

        return controller!!
      }
    }

    private fun stop(context: Context, fromTimeout: Boolean = false) {
      SafeForegroundService.stop(context, BackupProgressService::class.java, fromTimeout)
      controllerLock.withLock {
        controller = null
      }
    }

    private fun getForegroundNotification(context: Context): Notification {
      return NotificationCompat.Builder(context, NotificationChannels.getInstance().OTHER)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setProgress(100, (progress * 100).toInt(), indeterminate)
        .setContentIntent(PendingIntent.getActivity(context, 0, MainActivity.clearTop(context), PendingIntentFlags.mutable()))
        .setVibrate(longArrayOf(0))
        .build()
    }
  }

  override val tag: String = TAG
  override val notificationId: Int = NotificationIds.BACKUP_PROGRESS

  override fun getForegroundNotification(intent: Intent): Notification {
    return getForegroundNotification(this)
  }

  override fun onTimeout(startId: Int, fgsType: Int) {
    Log.w(TAG, "BackupProgressService has timed out. startId: $startId, foregroundServiceType: $fgsType")
    stop(context = this, fromTimeout = true)
  }

  /**
   * Use to update notification progress/state.
   */
  class Controller(private val context: Context, startingTitle: String) : AutoCloseable {

    init {
      title = startingTitle
      progress = 0f
      indeterminate = true
    }

    fun update(title: String, progress: Float, indeterminate: Boolean) {
      controllerLock.withLock {
        if (this != controller) {
          return
        }

        BackupProgressService.title = title
        BackupProgressService.progress = progress
        BackupProgressService.indeterminate = indeterminate

        if (NotificationManagerCompat.from(context).activeNotifications.any { n -> n.id == NotificationIds.BACKUP_PROGRESS }) {
          NotificationManagerCompat.from(context).notify(NotificationIds.BACKUP_PROGRESS, getForegroundNotification(context))
        }
      }
    }

    override fun close() {
      controllerLock.withLock {
        if (this == controller) {
          stop(context)
        }
      }
    }
  }
}
