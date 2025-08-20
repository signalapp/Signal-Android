/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import android.os.SystemClock
import org.webrtc.PeerConnection
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Provide an in-memory cache of TURN servers used for calling as the endpoint
 * is rate limited and the data should be valid for the duration of a app run.
 */
object TurnServerCache {
  private var iceServers: List<PeerConnection.IceServer>? = null
  private var lastUpdated: Duration = Duration.ZERO
  private var cacheTtl: Duration = Duration.ZERO

  @JvmStatic
  fun getCachedServers(): List<PeerConnection.IceServer>? {
    val now = SystemClock.elapsedRealtime().milliseconds

    return if (iceServers != null && now > lastUpdated && now < (lastUpdated + cacheTtl)) {
      iceServers
    } else {
      null
    }
  }

  @JvmStatic
  fun updateCache(newServers: List<PeerConnection.IceServer>, ttl: Long) {
    lastUpdated = SystemClock.elapsedRealtime().milliseconds
    cacheTtl = ttl.seconds

    iceServers = newServers
  }
}
