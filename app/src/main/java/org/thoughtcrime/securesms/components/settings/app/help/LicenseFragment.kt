/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import androidx.compose.runtime.Composable
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter

class LicenseFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter()

  @Composable
  override fun FragmentContent() {
    LicenseEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}
