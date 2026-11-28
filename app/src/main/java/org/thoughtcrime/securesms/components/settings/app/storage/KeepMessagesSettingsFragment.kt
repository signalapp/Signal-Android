/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import androidx.compose.runtime.Composable
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter

/**
 * Lets the user pick how long messages are kept before they're automatically deleted.
 */
class KeepMessagesSettingsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter()

  @Composable
  override fun FragmentContent() {
    KeepMessagesSettingsEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}
