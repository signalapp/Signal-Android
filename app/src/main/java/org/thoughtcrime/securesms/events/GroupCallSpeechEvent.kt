/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.events

import org.signal.ringrtc.GroupCall.SpeechEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class GroupCallSpeechEvent @JvmOverloads constructor(
  val speechEvent: SpeechEvent,
  private val timestampMs: Long = System.currentTimeMillis()
) {
  fun getCollapseTimestamp(): Duration {
    return timestampMs.milliseconds + LIFESPAN
  }

  companion object {
    private val LIFESPAN = 4L.seconds
  }
}
