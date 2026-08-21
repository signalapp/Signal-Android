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

class SoundsAndNotificationsSettingsFragment : ComposeFragment() {

  private val viewModel: SoundsAndNotificationsSettingsViewModel by viewModels(
    factoryProducer = {
      val recipientId = SoundsAndNotificationsSettingsFragmentArgs.fromBundle(requireArguments()).recipientId
      SoundsAndNotificationsSettingsViewModel.Factory(recipientId)
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
            val action = SoundsAndNotificationsSettingsFragmentDirections
              .actionSoundsAndNotificationsSettingsFragmentToCustomNotificationsSettingsFragment(state.recipientId)
            Navigation.findNavController(requireView()).safeNavigate(action)
          }
          is SoundsAndNotificationsEvent.NavigateToMutedNotifications -> {
            val action = SoundsAndNotificationsSettingsFragmentDirections
              .actionSoundsAndNotificationsSettingsFragmentToMutedNotificationsFragment(state.recipientId)
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
