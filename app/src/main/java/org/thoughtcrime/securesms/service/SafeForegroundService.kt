/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobs.ForegroundServiceUtil
import org.thoughtcrime.securesms.jobs.UnableToStartException
import kotlin.jvm.Throws

/**
 * A simple parent class meant to encourage the safe usage of foreground services.
 * Specifically, it ensures that both starting and "stopping" are done through
 * service starts and that we _always_ post a foreground notification.
 */
abstract class SafeForegroundService : Service() {

  companion object {
    private val TAG = Log.tag(SafeForegroundService::class.java)

    private const val ACTION_START = "start"
    private const val ACTION_STOP = "stop"

    /**
     * Safety starts the target foreground service.
     * Important: This operation can fail. If it does, [UnableToStartException] is thrown.
     */
    @Throws(UnableToStartException::class)
    fun start(context: Context, serviceClass: Class<out SafeForegroundService>) {
      val intent = Intent(context, serviceClass).apply {
        action = ACTION_START
      }

      ForegroundServiceUtil.startWhenCapable(context, intent)
    }

    /**
     * Safely stops the service by starting it with an action to stop itself.
     * This is done to prevent scenarios where you stop the service while
     * a start is pending, preventing the posting of a foreground notification.
     */
    fun stop(context: Context, serviceClass: Class<out SafeForegroundService>) {
      val intent = Intent(context, serviceClass).apply {
        action = ACTION_STOP
      }

      try {
        ForegroundServiceUtil.startWhenCapable(context, intent)
      } catch (e: UnableToStartException) {
        Log.w(TAG, "Failed to start service class $serviceClass", e)
      }
    }
  }

  override fun onCreate() {
    Log.d(tag, "[onCreate]")
    super.onCreate()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    checkNotNull(intent) { "Must have an intent!" }

    Log.d(tag, "[onStartCommand] action: ${intent.action}")

    startForeground(notificationId, getForegroundNotification(intent))

    when (val action = intent.action) {
      ACTION_START -> {
        onServiceStarted(intent)
      }
      ACTION_STOP -> {
        onServiceStopped(intent)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
      else -> Log.w(tag, "Unknown action: $action")
    }

    return START_NOT_STICKY
  }

  override fun onDestroy() {
    Log.d(tag, "[onDestroy]")
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  /** Log tag for improved logging */
  abstract val tag: String

  /** Notification ID to use when posting the foreground notification */
  abstract val notificationId: Int

  /** Notification to post as our foreground notification. */
  abstract fun getForegroundNotification(intent: Intent): Notification

  /** Event listener for when the service is started via an intent. */
  open fun onServiceStarted(intent: Intent) = Unit

  /** Event listener for when the service is stopped via an intent. */
  open fun onServiceStopped(intent: Intent) = Unit
}
