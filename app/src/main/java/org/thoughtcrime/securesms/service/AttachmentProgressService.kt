/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.logging.Log
import org.signal.core.util.throttleLatest
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.milliseconds

/**
 * A service to show attachment progress. In order to ensure we only show one status notification,
 * this handles both compression progress and upload progress.
 *
 * This class has a bunch of stuff to allow multiple people to "start" this service, but write to a
 * single notification. That way if something is compressing while something else is uploading,
 * or if there's two things uploading, we just have the one notification.
 *
 * To do this, it maintains a set of controllers. The service is told when those controllers change,
 * and it will only render the oldest controller in the set.
 *
 * We also use the number of controllers to determine if we actually need to start/stop the actual service.
 */
class AttachmentProgressService : SafeForegroundService() {
  companion object {
    private val TAG = Log.tag(AttachmentProgressService::class.java)

    private var title: String = ""
    private var progress: Float = 0f
      set(value) {
        field = value.coerceIn(0f, 1f)
      }
    private var indeterminate: Boolean = false

    private val listeners: MutableSet<() -> Unit> = CopyOnWriteArraySet()
    private val controllers: LinkedHashSet<Controller> = linkedSetOf()
    private val controllerLock = ReentrantLock()

    /**
     * Start the service with the provided [title]. You will receive a controllers that you can
     * use to update the notification.
     *
     * Important: This could fail to start! We do our best to start the service regardless of context,
     * but it will fail on some devices. If this happens, the returned [Controller] will be null.
     */
    @JvmStatic
    fun start(context: Context, title: String): Controller? {
      controllerLock.withLock {
        val started = if (controllers.isEmpty()) {
          Log.i(TAG, "[start] First controller. Starting.")
          start(context, AttachmentProgressService::class.java)
        } else {
          Log.i(TAG, "[start] No need to start the service again. Already have an active controller.")
          true
        }

        return if (started) {
          val controller = Controller(context, title)
          controllers += controller
          onControllersChanged(context)
          controller
        } else {
          null
        }
      }
    }

    private fun stop(context: Context) {
      stop(context, AttachmentProgressService::class.java)
    }

    private fun onControllersChanged(context: Context) {
      controllerLock.withLock {
        if (controllers.isNotEmpty()) {
          val originalTitle = title
          val originalProgress = progress
          val originalIndeterminate = indeterminate

          title = controllers.first().title
          progress = controllers.first().progress
          indeterminate = controllers.first().indeterminate

          if (originalTitle != title || originalProgress != progress || originalIndeterminate != indeterminate) {
            listeners.forEach { it() }
          }
        } else {
          Log.i(TAG, "[onControllersChanged] No controllers remaining. Stopping.")
          stop(context)
        }
      }
    }
  }

  val listener = {
    startForeground(notificationId, getForegroundNotification(Intent()))
  }

  override val tag: String = TAG

  override val notificationId: Int = NotificationIds.ATTACHMENT_PROGRESS

  override fun getForegroundNotification(intent: Intent): Notification {
    return NotificationCompat.Builder(this, NotificationChannels.getInstance().OTHER)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(title)
      .setProgress(100, (progress * 100).toInt(), indeterminate)
      .setContentIntent(PendingIntent.getActivity(this, 0, MainActivity.clearTop(this), PendingIntentFlags.mutable()))
      .setVibrate(longArrayOf(0))
      .build()
  }

  override fun onCreate() {
    super.onCreate()
    listeners += listener
  }

  override fun onDestroy() {
    super.onDestroy()
    listeners -= listener
  }

  class Controller(private val context: Context, title: String) : AutoCloseable {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val progressFlow = MutableSharedFlow<Float>(replay = 0, extraBufferCapacity = 1)

    init {
      coroutineScope.launch {
        progressFlow
          .throttleLatest(500.milliseconds) // avoid OS notification rate limiting
          .collectLatest { progress = it }
      }
    }

    var title: String = title
      set(value) {
        field = value
        onControllersChanged(context)
      }

    var progress: Float = 0f
      private set(value) {
        field = value
        indeterminate = false
        onControllersChanged(context)
      }

    var indeterminate: Boolean = false
      private set

    /** Has to have separate setter to avoid infinite loops when [progress] and [indeterminate] interact. */
    fun setIndeterminate(value: Boolean) {
      progress = 0f
      indeterminate = value
      onControllersChanged(context)
    }

    fun updateProgress(progress: Float) {
      progressFlow.tryEmit(progress)
    }

    override fun close() {
      controllerLock.withLock {
        coroutineScope.cancel()
        controllers.remove(this)
        onControllersChanged(context)
      }
    }
  }
}
