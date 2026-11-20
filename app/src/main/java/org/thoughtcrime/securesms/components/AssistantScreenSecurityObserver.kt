/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.signal.core.ui.initializeScreenshotSecurity
import org.signal.core.util.logging.Log
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.util.Collections
import java.util.WeakHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Watches for an assistant app (like Gemini) requesting the contents of the screen. When that happens and screen
 * security is off, screen security is turned on and [AssistantScreenSecurityBottomSheet] is shown, which lets the user
 * turn it back off. If they do, they won't be prompted again for [REPROMPT_INTERVAL].
 *
 * Recents also requests assist data, but only after the launcher has taken window focus, so requests made while the
 * activity lacks focus are ignored.
 *
 * Taking a screenshot can also request assist data, and it does so while the activity still has focus. On API 34+ we
 * listen for screenshots while screen security is off and ignore any request that lands next to one. The screenshot
 * can be reported after the request, so the request is acted on after a short delay.
 */
class AssistantScreenSecurityObserver : Application.OnProvideAssistDataListener, Application.ActivityLifecycleCallbacks {

  companion object {
    private val TAG = Log.tag(AssistantScreenSecurityObserver::class)
    private val REPROMPT_INTERVAL = 90.days

    /** How long before an assist request a screenshot still counts as its cause. */
    private val SCREENSHOT_LOOKBACK = 3.seconds

    /** How long we wait after an assist request to see if a screenshot shows up. */
    private val SCREENSHOT_GRACE_PERIOD = 2.seconds
  }

  private val handler = Handler(Looper.getMainLooper())
  private val screenCaptureCallbacks = WeakHashMap<Activity, Activity.ScreenCaptureCallback>()
  private val startedActivities: MutableSet<Activity> = Collections.newSetFromMap(WeakHashMap())
  private var lastScreenshotAt: Long = -1

  init {
    if (Build.VERSION.SDK_INT >= 34) {
      MainScope().launch {
        SignalStore.settings.isScreenSecurityEnabledFlow.collect {
          startedActivities.forEach { updateScreenCaptureCallback(it) }
        }
      }
    }
  }

  override fun onProvideAssistData(activity: Activity, data: Bundle) {
    if (activity !is FragmentActivity || !shouldEnableScreenSecurity(activity)) {
      return
    }

    if (!activity.hasWindowFocus()) {
      Log.i(TAG, "Ignoring assist request without window focus. Likely from recents.")
      return
    }

    if (Build.VERSION.SDK_INT < 34) {
      enableScreenSecurity(activity)
      return
    }

    if (wasScreenshotTakenSince(SystemClock.elapsedRealtime() - SCREENSHOT_LOOKBACK.inWholeMilliseconds)) {
      Log.i(TAG, "Ignoring assist request. A screenshot was just taken.")
      return
    }

    val requestedAt = SystemClock.elapsedRealtime()
    handler.postDelayed({
      if (wasScreenshotTakenSince(requestedAt)) {
        Log.i(TAG, "Ignoring assist request. A screenshot was taken right after.")
      } else if (shouldEnableScreenSecurity(activity)) {
        enableScreenSecurity(activity)
      }
    }, SCREENSHOT_GRACE_PERIOD.inWholeMilliseconds)
  }

  private fun shouldEnableScreenSecurity(activity: FragmentActivity): Boolean {
    if (!RemoteConfig.automaticScreenSecurity) {
      return false
    }

    if (SignalStore.settings.isScreenSecurityEnabled || activity.isFinishing || activity.isDestroyed) {
      return false
    }

    val timeSinceDeclined = (System.currentTimeMillis() - SignalStore.settings.assistantScreenSecurityDeclinedAt).milliseconds
    if (timeSinceDeclined in Duration.ZERO..<REPROMPT_INTERVAL) {
      Log.i(TAG, "Ignoring assist request. User declined $timeSinceDeclined ago.")
      return false
    }

    return true
  }

  private fun enableScreenSecurity(activity: FragmentActivity) {
    if (activity.supportFragmentManager.isStateSaved) {
      Log.i(TAG, "Ignoring assist request. Activity can no longer show the prompt.")
      return
    }

    Log.i(TAG, "Assistant requested screen contents. Enabling screen security.")
    SignalStore.settings.isScreenSecurityEnabled = true
    activity.window.initializeScreenshotSecurity()

    AssistantScreenSecurityBottomSheet.show(activity.supportFragmentManager)
  }

  private fun wasScreenshotTakenSince(elapsedRealtime: Long): Boolean {
    return lastScreenshotAt >= 0 && lastScreenshotAt >= elapsedRealtime
  }

  override fun onActivityStarted(activity: Activity) {
    startedActivities += activity
    updateScreenCaptureCallback(activity)
  }

  override fun onActivityStopped(activity: Activity) {
    startedActivities -= activity
    if (Build.VERSION.SDK_INT >= 34) {
      unregisterScreenCaptureCallback(activity)
    }
  }

  private fun updateScreenCaptureCallback(activity: Activity) {
    if (Build.VERSION.SDK_INT < 34) {
      return
    }

    if (SignalStore.settings.isScreenSecurityEnabled) {
      unregisterScreenCaptureCallback(activity)
    } else {
      registerScreenCaptureCallback(activity)
    }
  }

  @RequiresApi(34)
  private fun registerScreenCaptureCallback(activity: Activity) {
    if (screenCaptureCallbacks.containsKey(activity)) {
      return
    }

    val callback = Activity.ScreenCaptureCallback { lastScreenshotAt = SystemClock.elapsedRealtime() }

    activity.registerScreenCaptureCallback(activity.mainExecutor, callback)
    screenCaptureCallbacks[activity] = callback
  }

  @RequiresApi(34)
  private fun unregisterScreenCaptureCallback(activity: Activity) {
    val callback = screenCaptureCallbacks.remove(activity) ?: return
    activity.unregisterScreenCaptureCallback(callback)
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
  override fun onActivityResumed(activity: Activity) = Unit
  override fun onActivityPaused(activity: Activity) = Unit
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
  override fun onActivityDestroyed(activity: Activity) = Unit
}
