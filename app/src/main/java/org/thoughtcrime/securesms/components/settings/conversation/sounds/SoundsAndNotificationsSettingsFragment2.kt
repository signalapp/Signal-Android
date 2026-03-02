/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.sounds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.Navigation
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.MuteDialog
import org.thoughtcrime.securesms.components.settings.conversation.preferences.Utils.formatMutedUntil
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class SoundsAndNotificationsSettingsFragment2 : ComposeFragment() {

  private val viewModel: SoundsAndNotificationsSettingsViewModel2 by viewModels(
    factoryProducer = {
      val recipientId = SoundsAndNotificationsSettingsFragment2Args.fromBundle(requireArguments()).recipientId
      SoundsAndNotificationsSettingsViewModel2.Factory(recipientId)
    }
  )

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (!state.channelConsistencyCheckComplete || state.recipientId == Recipient.UNKNOWN.id) {
      return
    }

    SoundsAndNotificationsSettingsScreen(
      state = state,
      formatMuteUntil = { it.formatMutedUntil(requireContext()) },
      onEvent = { event ->
        when (event) {
          is SoundsAndNotificationsEvent.NavigateToCustomNotifications -> {
            val action = SoundsAndNotificationsSettingsFragment2Directions
              .actionSoundsAndNotificationsSettingsFragment2ToCustomNotificationsSettingsFragment(state.recipientId)
            Navigation.findNavController(requireView()).safeNavigate(action)
          }
          else -> viewModel.onEvent(event)
        }
      },
      onNavigationClick = {
        requireActivity().onBackPressedDispatcher.onBackPressed()
      },
      onMuteClick = {
        MuteDialog.show(requireContext(), childFragmentManager, viewLifecycleOwner) { muteUntil ->
          viewModel.onEvent(SoundsAndNotificationsEvent.SetMuteUntil(muteUntil))
        }
      }
    )
  }
}
