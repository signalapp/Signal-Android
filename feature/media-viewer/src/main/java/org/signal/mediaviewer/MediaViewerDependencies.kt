/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer

import androidx.media3.exoplayer.ExoPlayer
import org.signal.video.exo.ExoPlayerPool

/**
 * Media viewer feature module dependencies.
 */
object MediaViewerDependencies {
  private lateinit var _provider: Provider

  @Synchronized
  fun init(provider: Provider) {
    if (this::_provider.isInitialized) {
      return
    }

    _provider = provider
  }

  val exoPlayerPool: ExoPlayerPool<ExoPlayer>
    get() = _provider.provideExoPlayerPool()

  interface Provider {
    fun provideExoPlayerPool(): ExoPlayerPool<ExoPlayer>
  }
}
