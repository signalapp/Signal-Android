/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing

import android.os.Bundle
import androidx.activity.compose.setContent
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.components.settings.app.appearance.AppearanceSettingsEntry

/**
 * Hosts the appearance settings screen outside the registration gate, so the language picker can be used on an
 * unregistered install. The language-split-tests module starts it to see which language the app comes up in. Launch
 * by hand with `am start -n org.thoughtcrime.securesms/org.thoughtcrime.securesms.testing.LanguageSplitTestActivity`.
 */
class LanguageSplitTestActivity : BaseActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      SignalTheme {
        AppearanceSettingsEntry(parentEventEmitter = {})
      }
    }
  }
}
