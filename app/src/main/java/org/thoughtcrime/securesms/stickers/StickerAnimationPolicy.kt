/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers

import org.signal.apng.ApngDrawable
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.AnimationPlaybackPolicy

/**
 * Central policy for whether animated (APNG) stickers should play in chat.
 */
object StickerAnimationPolicy {

  @JvmStatic
  fun allowAnimation(): Boolean = SignalStore.settings.isAutoplayStickersAndGifsEnabled

  /**
   * Puts a sticker that has come into view in a conversation back into the state it starts in: playing a single
   * playback when autoplay is enabled, and resting on its first frame otherwise.
   */
  @JvmStatic
  fun resetPlayback(drawable: ApngDrawable) {
    if (allowAnimation()) {
      playSinglePlayback(drawable)
    } else {
      drawable.stopAtStart()
    }
  }

  @JvmStatic
  fun playSinglePlayback(drawable: ApngDrawable) {
    drawable.play(AnimationPlaybackPolicy.loopsOfSinglePlayback(drawable.durationMs))
  }
}
