package org.thoughtcrime.securesms.components.settings.conversation.grouppermissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.chatsettings.screens.grouppermissions.GroupPermissionsScreen
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.util.viewModel

/**
 * Fragment wrapping [GroupPermissionsScreen] to allow an admin to set which actions non-admins can take in a group.
 */
class GroupPermissionsFragment : ComposeFragment() {

  private val viewModel: GroupPermissionsViewModel by viewModel {
    GroupPermissionsViewModel(GroupPermissionsFragmentArgs.fromBundle(requireArguments()).groupId)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    GroupPermissionsScreen(
      state = state,
      onEvent = viewModel::onEvent,
      onNavigationClick = {
        requireActivity().onBackPressedDispatcher.onBackPressed()
      }
    )
  }
}
