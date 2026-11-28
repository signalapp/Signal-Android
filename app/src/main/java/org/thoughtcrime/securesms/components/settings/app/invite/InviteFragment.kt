/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.invite

import androidx.compose.runtime.Composable
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter

/**
 * Fragment when inviting someone to use Signal
 */
class InviteFragment : ComposeFragment() {
  private val appSettingsRouter by appSettingsRouter()

  @Composable
  override fun FragmentContent() {
    InviteEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}
