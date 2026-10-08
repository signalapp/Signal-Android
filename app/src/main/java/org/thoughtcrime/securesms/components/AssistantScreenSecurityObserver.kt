/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewStructure
import androidx.core.view.children
import androidx.fragment.app.FragmentActivity
import org.signal.core.ui.initializeScreenshotSecurity
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Watches every activity for an assistant app (like Gemini) requesting the contents of the screen. When that happens
 * and screen security is off, screen security is turned on and [AssistantScreenSecurityBottomSheet] is shown, which lets
 * the user turn it back off. If they do, they won't be prompted again for [REPROMPT_INTERVAL].
 */
class AssistantScreenSecurityObserver : Application.ActivityLifecycleCallbacks {

  companion object {
    private val TAG = Log.tag(AssistantScreenSecurityObserver::class)
    private val REPROMPT_INTERVAL = 90.days
  }

  override fun onActivityResumed(activity: Activity) {
    if (!RemoteConfig.internalUser || activity !is FragmentActivity) {
      return
    }

    // The normal activity-level assist callbacks can fire for the multitasking view and such.
    // To avoid that, we add the callbacks to a view, which should only be requested by the assistant.
    val decorView = activity.window.peekDecorView() as? ViewGroup ?: return
    val alreadyAdded = decorView.children.any { it is AssistRequestDetectorView }
    if (!alreadyAdded) {
      decorView.addView(AssistRequestDetectorView(activity) { onAssistRequested(activity) })
    }
  }

  private fun onAssistRequested(activity: FragmentActivity) {
    if (SignalStore.settings.isScreenSecurityEnabled) {
      return
    }

    val timeSinceDeclined = (System.currentTimeMillis() - SignalStore.settings.assistantScreenSecurityDeclinedAt).milliseconds
    if (timeSinceDeclined in Duration.ZERO..<REPROMPT_INTERVAL) {
      return
    }

    if (activity.isFinishing || activity.isDestroyed) {
      return
    }

    Log.i(TAG, "Assistant requested screen contents. Enabling screen security.")
    SignalStore.settings.isScreenSecurityEnabled = true
    activity.window.initializeScreenshotSecurity()

    AssistantScreenSecurityBottomSheet.show(activity.supportFragmentManager)
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
  override fun onActivityStarted(activity: Activity) = Unit
  override fun onActivityPaused(activity: Activity) = Unit
  override fun onActivityStopped(activity: Activity) = Unit
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
  override fun onActivityDestroyed(activity: Activity) = Unit

  private class AssistRequestDetectorView(context: Context, private val onAssistRequested: () -> Unit) : View(context) {

    init {
      visibility = GONE
      importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
      if (Build.VERSION.SDK_INT >= 26) {
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
      }
      if (Build.VERSION.SDK_INT >= 30) {
        importantForContentCapture = IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
      }
    }

    override fun onProvideStructure(structure: ViewStructure) {
      super.onProvideStructure(structure)
      post(onAssistRequested)
    }
  }
}
