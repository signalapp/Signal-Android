/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.help

import androidx.compose.runtime.Composable
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter

class HelpFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter()

  @Composable
  override fun FragmentContent() {
    HelpEntry(
      startCategoryIndex = arguments?.getInt(START_CATEGORY_INDEX, 0) ?: 0,
      parentEventEmitter = appSettingsRouter::onEvent
    )
  }

  companion object {
    const val START_CATEGORY_INDEX = "start_category_index"
    const val PAYMENT_INDEX = 6
    const val DONATION_INDEX = 7
    const val REMOTE_BACKUPS_INDEX = 8
  }
}
