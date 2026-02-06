/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import org.signal.core.ui.CoreUiDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.TextSecurePreferences

object CoreUiDependenciesProvider : CoreUiDependencies.Provider {
  override fun provideIsIncognitoKeyboardEnabled(): Boolean {
    return TextSecurePreferences.isIncognitoKeyboardEnabled(AppDependencies.application)
  }

  override fun provideIsScreenSecurityEnabled(): Boolean {
    return TextSecurePreferences.isScreenSecurityEnabled(AppDependencies.application)
  }

  override fun provideForceSplitPane(): Boolean {
    return SignalStore.internal.forceSplitPane
  }
}
