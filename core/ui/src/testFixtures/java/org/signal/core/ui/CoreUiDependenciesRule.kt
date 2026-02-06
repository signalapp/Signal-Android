/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import org.junit.rules.ExternalResource

/**
 * Since tests that depend on [org.signal.core.ui.compose.theme.SignalTheme] need
 * [CoreUiDependencies] to be initialized, this rule provides a convenient way to do so.
 */
class CoreUiDependenciesRule(
  private val isIncognitoKeyboardEnabled: Boolean = false
) : ExternalResource() {
  override fun before() {
    CoreUiDependencies.init(Provider(isIncognitoKeyboardEnabled))
  }

  private class Provider(
    val isIncognitoKeyboardEnabled: Boolean
  ): CoreUiDependencies.Provider {
    override fun provideIsIncognitoKeyboardEnabled(): Boolean = isIncognitoKeyboardEnabled
    override fun provideIsScreenSecurityEnabled(): Boolean = false
    override fun provideForceSplitPane(): Boolean = false
  }
}
