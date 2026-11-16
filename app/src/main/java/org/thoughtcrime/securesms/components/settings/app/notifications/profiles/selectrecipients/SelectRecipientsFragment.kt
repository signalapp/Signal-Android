/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.selectrecipients

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter

/**
 * Contact selection for choosing who a notification profile allows through.
 */
class SelectRecipientsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateBack = { findNavController().navigateUp() })

  @Composable
  override fun FragmentContent() {
    val args = SelectRecipientsFragmentArgs.fromBundle(requireArguments())

    SelectRecipientsEntry(
      profileId = args.profileId,
      currentMembers = args.currentSelection.orEmpty().toSet(),
      parentEventEmitter = appSettingsRouter::onEvent
    )
  }
}
