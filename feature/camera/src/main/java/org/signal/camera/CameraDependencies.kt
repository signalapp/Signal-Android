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

  interface Provider {
    fun isStoriesFeatureEnabled(): Boolean
  }
}
