/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.glide

import android.app.Application
import android.net.Uri
import org.signal.glide.common.io.InputStreamFactory

/**
 * Dependencies for the Glide library module, provided by the host application.
 */
object SignalGlideDependencies {
  private lateinit var _application: Application
  private lateinit var _provider: Provider
  
  @JvmStatic
  @Synchronized
  fun init(application: Application, provider: Provider) {
    if (this::_application.isInitialized || this::_provider.isInitialized) {
      return
    }
    
    _application = application
    _provider = provider
  }
  
  val application: Application
    get() = _application

  fun getUriInputStreamFactory(uri: Uri): InputStreamFactory = _provider.getUriInputStreamFactory(uri)
  
  interface Provider {
    /**
     * A factory which can create an [java.io.InputStream] from a given [Uri]
     */
    fun getUriInputStreamFactory(uri: Uri): InputStreamFactory
  }
}