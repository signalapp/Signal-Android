/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.app.Application
import org.signal.mediasend.preupload.PreUploadRepository

/**
 * MediaSend Feature Module dependencies
 */
object MediaSendDependencies {
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

  val preUploadRepository: PreUploadRepository
    get() = _provider.providePreUploadRepository()

  val mediaSendRepository: MediaSendRepository
    get() = _provider.provideMediaSendRepository()

  interface Provider {
    fun provideMediaSendRepository(): MediaSendRepository
    fun providePreUploadRepository(): PreUploadRepository
  }
}
