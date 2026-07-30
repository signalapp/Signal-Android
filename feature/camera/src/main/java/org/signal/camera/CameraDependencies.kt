/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera

import android.app.Application

/**
 * Camera Feature Module dependencies
 */
object CameraDependencies {
  /** Bitrate used when the embedder has no transcoding config of its own. Matches the highest default 720p target. */
  const val DEFAULT_MAX_VIDEO_BITRATE_BPS = 4_000_000

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

  fun isStoriesFeatureEnabled(): Boolean {
    return _provider.isStoriesFeatureEnabled()
  }

  fun getMaxVideoBitrateBps(): Int {
    return _provider.getMaxVideoBitrateBps()
  }

  interface Provider {
    fun isStoriesFeatureEnabled(): Boolean

    /** The highest video bitrate, in bits per second, that captured video may be transcoded to. */
    fun getMaxVideoBitrateBps(): Int = DEFAULT_MAX_VIDEO_BITRATE_BPS
  }
}
