/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.hidden

import androidx.compose.runtime.Composable
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter

/**
 * Fragment host for [HiddenContactsEntry] until app settings moves to Nav3.
 */
class HiddenContactsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter()

  @Composable
  override fun FragmentContent() {
    HiddenContactsEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}
