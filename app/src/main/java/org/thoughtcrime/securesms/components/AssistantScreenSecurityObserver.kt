/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import org.signal.core.ui.initializeScreenshotSecurity
import org.signal.core.util.logging.Log
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Watches for an assistant app (like Gemini) requesting the contents of the screen. When that happens and screen
 * security is off, screen security is turned on and [AssistantScreenSecurityBottomSheet] is shown, which lets the user
 * turn it back off. If they do, they won't be prompted again for [REPROMPT_INTERVAL].
 *
 * Recents also requests assist data, but only after the launcher has taken window focus, so requests made while the
 * activity lacks focus are ignored.
 */
class AssistantScreenSecurityObserver : Application.OnProvideAssistDataListener {

  companion object {
    private val TAG = Log.tag(AssistantScreenSecurityObserver::class)
    private val REPROMPT_INTERVAL = 90.days
  }

  override fun onProvideAssistData(activity: Activity, data: Bundle) {
    if (!RemoteConfig.automaticScreenSecurity || activity !is FragmentActivity) {
      return
    }

    if (SignalStore.settings.isScreenSecurityEnabled || activity.isFinishing || activity.isDestroyed) {
      return
    }

    if (!activity.hasWindowFocus()) {
      Log.i(TAG, "Ignoring assist request without window focus. Likely from recents.")
      return
    }

    val timeSinceDeclined = (System.currentTimeMillis() - SignalStore.settings.assistantScreenSecurityDeclinedAt).milliseconds
    if (timeSinceDeclined in Duration.ZERO..<REPROMPT_INTERVAL) {
      Log.i(TAG, "Ignoring assist request. User declined $timeSinceDeclined ago.")
      return
    }

    Log.i(TAG, "Assistant requested screen contents. Enabling screen security.")
    SignalStore.settings.isScreenSecurityEnabled = true
    activity.window.initializeScreenshotSecurity()

    AssistantScreenSecurityBottomSheet.show(activity.supportFragmentManager)
  }
}
