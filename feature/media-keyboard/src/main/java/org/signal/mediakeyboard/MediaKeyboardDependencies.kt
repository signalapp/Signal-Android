/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard

import android.app.Application
import androidx.media3.exoplayer.ExoPlayer
import org.signal.video.exo.ExoPlayerPool

/**
 * Media keyboard feature module dependencies.
 */
object MediaKeyboardDependencies {
  private lateinit var _application: Application
  private lateinit var _provider: Provider

  @Synchronized
  fun init(application: Application, provider: Provider) {
    if (this::_application.isInitialized || this::_provider.isInitialized) {
      return
    }

    _application = application
    _provider = provider
  }

  val application
    get() = _application

  /**
   * The app-wide pool of players. Shared with every other surface that plays video, because the number of
   * simultaneous decoders is a device limit rather than a per-screen one.
   */
  val exoPlayerPool: ExoPlayerPool<ExoPlayer>
    get() = _provider.provideExoPlayerPool()

  interface Provider {
    fun provideExoPlayerPool(): ExoPlayerPool<ExoPlayer>
  }
}
