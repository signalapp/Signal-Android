/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import android.app.Application
import androidx.annotation.VisibleForTesting

object CoreUiDependencies {

  private lateinit var _application: Application
  private lateinit var _provider: Provider

  fun init(application: Application, provider: Provider) {
    if (this::_provider.isInitialized) {
      return
    }

    _application = application
    _provider = provider
  }

  /**
   * Replaces any existing provider, unlike [init]. Only for use in tests.
   */
  @VisibleForTesting
  fun testInject(application: Application, provider: Provider) {
    _application = application
    _provider = provider
  }

  val application: Application
    get() = _application

  val packageId: String
    get() = _provider.providePackageId()

  val isIncognitoKeyboardEnabled: Boolean
    get() = _provider.provideIsIncognitoKeyboardEnabled()

  val isScreenSecurityEnabled: Boolean
    get() = _provider.provideIsScreenSecurityEnabled()

  interface Provider {
    fun providePackageId(): String
    fun provideIsIncognitoKeyboardEnabled(): Boolean
    fun provideIsScreenSecurityEnabled(): Boolean
  }
}
