package org.thoughtcrime.securesms.components.settings.conversation.sounds.custom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.util.viewModel

/**
 * Fragment wrapping [CustomNotificationsSettingsScreen] to allow user to set custom notifications for a given recipient.
 */
class CustomNotificationsSettingsFragment : ComposeFragment() {

  private val viewModel: CustomNotificationsSettingsViewModel by viewModel {
    CustomNotificationsSettingsViewModel(CustomNotificationsSettingsFragmentArgs.fromBundle(requireArguments()).recipientId)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CustomNotificationsSettingsScreen(
      state = state,
      ringtonePickerRequests = viewModel.ringtonePickerRequests,
      onEvent = viewModel::onEvent,
      onNavigationClick = {
        requireActivity().onBackPressedDispatcher.onBackPressed()
      }
    )
  }
}
