/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

object CoreUiDependencies {

  private lateinit var _provider: Provider

  fun init(provider: Provider) {
    if (this::_provider.isInitialized) {
      return
    }

    _provider = provider
  }

  val isIncognitoKeyboardEnabled: Boolean
    get() = _provider.provideIsIncognitoKeyboardEnabled()

  interface Provider {
    fun provideIsIncognitoKeyboardEnabled(): Boolean
  }
}