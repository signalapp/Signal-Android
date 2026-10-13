/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.inline

import org.thoughtcrime.securesms.util.AnimationPlaybackPolicy

/**
 * Ends a looping video once it has played the number of loops [AnimationPlaybackPolicy] allows for its duration.
 */
class InlineVideoLoopPolicy(private val callback: Callback) {

  private var loopsRemaining = -1L

  fun setMediaDuration(duration: Long) {
    loopsRemaining = AnimationPlaybackPolicy.loopsOfSinglePlayback(duration).toLong()
  }

  /**
   * Called at the end of each loop. Returns true when playback should stop.
   */
  fun shouldEndPlayback(): Boolean {
    check(loopsRemaining >= 0) { "Must call setMediaDuration before calling this method." }

    if (loopsRemaining == 0L) {
      return true
    }

    loopsRemaining--
    if (loopsRemaining == 0L) {
      callback.onPlaybackWillEnd()
      return true
    }

    return false
  }

  fun interface Callback {
    fun onPlaybackWillEnd()
  }
}
