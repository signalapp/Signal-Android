/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.demo

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import org.signal.video.exo.ExoPlayerPool

/**
 * Plain pool for the demo app, which has none of the main app's media source wiring.
 */
class DemoExoPlayerPool(context: Context) : ExoPlayerPool<ExoPlayer>(0) {
  private val context = context.applicationContext

  override fun createPlayer(): ExoPlayer = ExoPlayer.Builder(this.context).build()

  override fun getMaxSimultaneousPlayback(): Int = MAX_SIMULTANEOUS_PLAYBACK

  companion object {
    private const val MAX_SIMULTANEOUS_PLAYBACK = 3
  }
}
