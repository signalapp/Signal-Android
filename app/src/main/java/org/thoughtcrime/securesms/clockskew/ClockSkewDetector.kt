/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.clockskew

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.signal.core.util.AppForegroundObserver
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Detects when the local device clock is too far out of sync with the server's clock and holds that state in memory.
 *
 * We learn the server's true time from two sources: the websocket ([org.signal.libsignal.net.ChatConnectionListener.onServerTimestamp],
 * routed through [org.thoughtcrime.securesms.net.SignalWebSocketHealthMonitor]) and the remote config fetch. Because we
 * hold the websocket open aggressively, a user can change their clock while already connected — in which case we won't
 * receive a fresh server time. To catch that, we cache the last-known server time alongside a monotonic
 * [SystemClock.elapsedRealtime] reading and re-check on foreground, estimating the current server time without needing
 * another network round-trip.
 *
 * When skew is detected we stop trying to keep the websocket open (so we don't reconnect in a loop) regardless of
 * whether the app is foregrounded, and — only while foregrounded — show [ClockSkewActivity]. The detection state is
 * intentionally not persisted and is re-evaluated via [recheck] whenever the app is backgrounded or foregrounded, so a
 * skew that has since been corrected clears itself and the user always gets a fresh attempt.
 *
 * Note that the monotonic reading is deliberately kept in memory only: it is meaningless across reboots, but a reboot
 * kills our process anyway, so a live cache is always from the current boot.
 */
object ClockSkewDetector {

  private val TAG = Log.tag(ClockSkewDetector::class)

  private val _detected = MutableStateFlow(false)
  val detected: StateFlow<Boolean> = _detected.asStateFlow()

  val isDetected: Boolean
    get() = _detected.value

  /** The amount our local clock was off from the server's when skew was detected. [Duration.ZERO] when not detected. */
  @Volatile
  var skew: Duration = Duration.ZERO
    private set

  @Volatile
  private var lastServerTime: Long = 0

  @Volatile
  private var lastServerTimeElapsedRealtime: Long = 0

  private val allowedSkew: Duration
    get() = RemoteConfig.maxAllowedClockSkewSeconds.seconds

  /**
   * Records a freshly-observed server time (both persisting it and caching it for [recheck]) and immediately checks it
   * against our local clock.
   */
  fun onServerTimeReceived(serverTime: Long) {
    lastServerTime = serverTime
    lastServerTimeElapsedRealtime = SystemClock.elapsedRealtime()
    SignalStore.misc.setLastKnownServerTime(serverTime, System.currentTimeMillis())

    val skew = skewFrom(serverTime)
    if (skew > allowedSkew) {
      Log.w(TAG, "Local clock is off from the server by $skew, which exceeds the allowed limit. Blocking.", true)
      markDetected(skew)
    }
  }

  /**
   * Re-evaluates clock skew using our cached server time and a monotonic estimate of elapsed time, without needing a
   * network round-trip. Intended to be called when the app is foregrounded, to catch the case where the user changed
   * their clock while we were already connected. Clears the detection state if the clock now looks fine.
   */
  fun recheck() {
    if (lastServerTimeElapsedRealtime == 0L) {
      reset()
      return
    }

    val estimatedServerTime = lastServerTime + (SystemClock.elapsedRealtime() - lastServerTimeElapsedRealtime)
    val skew = skewFrom(estimatedServerTime)
    if (skew > allowedSkew) {
      Log.w(TAG, "Local clock is off from the estimated server time by $skew, which exceeds the allowed limit. Blocking.", true)
      markDetected(skew)
    } else {
      reset()
    }
  }

  /** Clears any detected skew, allowing the websocket to reconnect and re-check. */
  private fun reset() {
    skew = Duration.ZERO
    _detected.value = false
  }

  /**
   * Begins observing the detection state and launches [ClockSkewActivity] whenever clock skew is detected while the app
   * is foregrounded. Skew can also be detected while backgrounded (which still blocks the websocket), but we only bring
   * up the blocking screen when foregrounded, to avoid a background activity launch. Should be called once during app
   * startup.
   */
  @JvmStatic
  fun beginObserving(application: Application) {
    CoroutineScope(Dispatchers.Main).launch {
      detected.collect { detected ->
        if (detected && AppForegroundObserver.isForegrounded()) {
          application.startActivity(ClockSkewActivity.createIntent(application).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
      }
    }
  }

  private fun markDetected(skew: Duration) {
    this.skew = skew
    _detected.value = true
  }

  private fun skewFrom(serverTime: Long): Duration {
    return abs(System.currentTimeMillis() - serverTime).milliseconds
  }
}
