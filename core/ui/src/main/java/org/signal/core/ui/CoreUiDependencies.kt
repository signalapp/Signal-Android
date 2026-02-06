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

  val isScreenSecurityEnabled: Boolean
    get() = _provider.provideIsScreenSecurityEnabled()

  val forceSplitPane: Boolean
    get() = _provider.provideForceSplitPane()

  interface Provider {
    fun provideIsIncognitoKeyboardEnabled(): Boolean
    fun provideIsScreenSecurityEnabled(): Boolean
    fun provideForceSplitPane(): Boolean
  }
}