/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobs.ForegroundServiceUtil
import org.thoughtcrime.securesms.jobs.UnableToStartException
import java.util.concurrent.locks.ReentrantLock
import javax.annotation.CheckReturnValue
import kotlin.concurrent.withLock

/**
 * A simple parent class meant to encourage the safe usage of foreground services.
 * Specifically, it ensures that both starting and "stopping" are done through
 * service starts and that we _always_ post a foreground notification.
 */
abstract class SafeForegroundService : Service() {

  companion object {
    private val TAG = Log.tag(SafeForegroundService::class.java)

    private const val ACTION_START = "start"
    private const val ACTION_UPDATE = "update"
    private const val ACTION_STOP = "stop"

    private var states: MutableMap<Class<out SafeForegroundService>, State> = mutableMapOf()
    private val stateLock = ReentrantLock()

    /**
     * Safely starts the target foreground service.
     * @return False if we tried to start the service but failed, otherwise true.
     */
    @CheckReturnValue
    fun start(context: Context, serviceClass: Class<out SafeForegroundService>, extras: Bundle = Bundle.EMPTY): Boolean {
      stateLock.withLock {
        val state = currentState(serviceClass)

        Log.d(TAG, "[start] Current state: $state")

        return when (state) {
          State.STARTING,
          State.NEEDS_RESTART -> {
            Log.d(TAG, "[start] No need to start the service again. Current state: $state")
            true
          }
          State.STOPPED -> {
            Log.d(TAG, "[start] Starting service.")
            states[serviceClass] = State.STARTING
            try {
              ForegroundServiceUtil.startWhenCapable(
                context = context,
                intent = Intent(context, serviceClass).apply {
                  action = ACTION_START
                  putExtras(extras)
                }
              )
              true
            } catch (e: UnableToStartException) {
              Log.w(TAG, "[start] Failed to start the service!")
              states[serviceClass] = State.STOPPED
              false
            }
          }
          State.STOPPING -> {
            Log.d(TAG, "[start] Attempted to start while the service is stopping. Enqueueing a restart.")
            states[serviceClass] = State.NEEDS_RESTART
            true
          }
        }
      }
    }

    /**
     * Safely stops the service by starting it with an action to stop itself.
     * This is done to prevent scenarios where you stop the service while
     * a start is pending, preventing the posting of a foreground notification.
     * @return true if service was running previously
     */
    fun stop(context: Context, serviceClass: Class<out SafeForegroundService>): Boolean {
      stateLock.withLock {
        val state = currentState(serviceClass)

        Log.d(TAG, "[stop] Current state: $state")

        return when (state) {
          State.STARTING -> {
            Log.d(TAG, "[stop] Stopping service.")
            states[serviceClass] = State.STOPPING
            try {
              ForegroundServiceUtil.startWhenCapable(
                context = context,
                intent = Intent(context, serviceClass).apply { action = ACTION_STOP }
              )
            } catch (e: UnableToStartException) {
              Log.w(TAG, "Failed to start service class $serviceClass", e)
              states[serviceClass] = State.STOPPED
            }
            true
          }

          State.STOPPED,
          State.STOPPING -> {
            Log.d(TAG, "[stop] No need to stop the service. Current state: $state")
            false
          }

          State.NEEDS_RESTART -> {
            Log.i(TAG, "[stop] Clearing pending restart.")
            states[serviceClass] = State.STOPPING
            false
          }
        }
      }
    }

    /**
     * Safely updates the target foreground service if it is already starting.
     *
     * @return True if we updated a started service, otherwise false.
     */
    @CheckReturnValue
    fun update(context: Context, serviceClass: Class<out SafeForegroundService>, extras: Bundle = Bundle.EMPTY): Boolean {
      stateLock.withLock {
        val state = currentState(serviceClass)

        Log.d(TAG, "[update] Current state: $state")

        return when (state) {
          State.STARTING -> {
            Log.d(TAG, "[update] Updating service.")
            try {
              ForegroundServiceUtil.startWhenCapable(
                context = context,
                intent = Intent(context, serviceClass).apply {
                  action = ACTION_UPDATE
                  putExtras(extras)
                }
              )
              true
            } catch (e: UnableToStartException) {
              Log.w(TAG, "Failed to update service class $serviceClass", e)
              false
            }
          }

          else -> {
            Log.d(TAG, "[update] Service cannot be updated. Current state: $state")
            false
          }
        }
      }
    }

    fun isStopping(intent: Intent): Boolean {
      return intent.action == ACTION_STOP
    }

    private fun currentState(clazz: Class<out SafeForegroundService>): State {
      return states.getOrPut(clazz) { State.STOPPED }
    }
  }

  override fun onCreate() {
    Log.d(tag, "[onCreate]")
    super.onCreate()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    checkNotNull(intent) { "Must have an intent!" }

    Log.d(tag, "[onStartCommand] action: ${intent.action}")

    if (Build.VERSION.SDK_INT >= 30 && serviceType != 0) {
      startForeground(notificationId, getForegroundNotification(intent), serviceType)
    } else {
      startForeground(notificationId, getForegroundNotification(intent))
    }

    when (val action = intent.action) {
      ACTION_START -> {
        onServiceStartCommandReceived(intent)
      }
      ACTION_STOP -> {
        onServiceStopCommandReceived(intent)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
      ACTION_UPDATE -> {
        onServiceUpdateCommandReceived(intent)
      }
      else -> Log.w(tag, "Unknown action: $action")
    }

    return START_NOT_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()

    stateLock.withLock {
      val state = currentState(javaClass)

      Log.d(tag, "[onDestroy] Current state: $state")
      when (state) {
        State.STOPPED,
        State.STARTING,
        State.STOPPING -> {
          states[javaClass] = State.STOPPED
        }

        State.NEEDS_RESTART -> {
          Log.i(TAG, "[onDestroy] Restarting service!")
          states[javaClass] = State.STOPPED
          if (!start(this, javaClass)) {
            Log.w(TAG, "[onDestroy] Failed to restart service.")
          }
        }
      }
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  /** Log tag for improved logging */
  abstract val tag: String

  /** Notification ID to use when posting the foreground notification */
  abstract val notificationId: Int

  /** Special service type to use when calling start service if needed */
  @RequiresApi(30)
  open val serviceType: Int = 0

  /** Notification to post as our foreground notification. */
  abstract fun getForegroundNotification(intent: Intent): Notification

  /** Event listener for when the service is started via an intent. */
  open fun onServiceStartCommandReceived(intent: Intent) = Unit

  /** Event listener for when the service is stopped via an intent. */
  open fun onServiceStopCommandReceived(intent: Intent) = Unit

  /** Event listener for when the service is updated via an intent. */
  open fun onServiceUpdateCommandReceived(intent: Intent) = Unit

  private enum class State {
    /** The service is not running. */
    STOPPED,

    /** We told the service to start (via an intent). It may or may not be actually running yet. */
    STARTING,

    /** We told the service to stop (via an intent), but it's still technically running. */
    STOPPING,

    /** We requested that the service be started while it was in the process of stopping. */
    NEEDS_RESTART
  }
}
