package org.thoughtcrime.securesms.components.settings.conversation.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.util.viewModel

/**
 * Fragment wrapping [PermissionsSettingsScreen] to allow an admin to set which actions non-admins can take in a group.
 */
class PermissionsSettingsFragment : ComposeFragment() {

  private val viewModel: PermissionsSettingsViewModel by viewModel {
    PermissionsSettingsViewModel(PermissionsSettingsFragmentArgs.fromBundle(requireArguments()).groupId)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PermissionsSettingsScreen(
      state = state,
      onEvent = viewModel::onEvent,
      onNavigationClick = {
        requireActivity().onBackPressedDispatcher.onBackPressed()
      }
    )
  }
}
