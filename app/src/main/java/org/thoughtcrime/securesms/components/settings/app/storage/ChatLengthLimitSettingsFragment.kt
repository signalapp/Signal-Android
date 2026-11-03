/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import androidx.compose.runtime.Composable
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter

/**
 * Lets the user pick how many messages each chat is trimmed to.
 */
class ChatLengthLimitSettingsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter()

  override val autofillEnabled: Boolean = false

  @Composable
  override fun FragmentContent() {
    ChatLengthLimitSettingsEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}
