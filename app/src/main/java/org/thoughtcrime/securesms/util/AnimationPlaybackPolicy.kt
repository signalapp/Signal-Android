/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import kotlin.time.Duration.Companion.seconds

/**
 * How long a single playback of an animation (a gif or an animated sticker) runs before it stops.
 */
object AnimationPlaybackPolicy {

  private const val MIN_LOOPS = 3
  private val LOOP_WINDOW_MS = 6.seconds.inWholeMilliseconds

  /**
   * At least [MIN_LOOPS] loops, or as many as fit in the loop window for short animations.
   */
  @JvmStatic
  fun loopsOfSinglePlayback(durationMs: Long): Int {
    if (durationMs <= 0) {
      return MIN_LOOPS
    }

    return maxOf(MIN_LOOPS.toLong(), LOOP_WINDOW_MS / durationMs).toInt()
  }
}
