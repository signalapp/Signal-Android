package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.allowedmembers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.viewModel
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Show and allow addition of recipients to a profile during the create flow.
 */
class AddAllowedMembersFragment : ComposeFragment() {

  private val viewModel: AddAllowedMembersViewModel by viewModel {
    AddAllowedMembersViewModel(AddAllowedMembersFragmentArgs.fromBundle(requireArguments()).profileId)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    AddAllowedMembersScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: AddAllowedMembersAction) {
    when (action) {
      AddAllowedMembersAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
      is AddAllowedMembersAction.NavigateToSelectRecipients -> {
        findNavController().safeNavigate(
          AddAllowedMembersFragmentDirections.actionAddAllowedMembersFragmentToSelectRecipientsFragment(action.profileId)
            .setCurrentSelection(action.currentSelection.toTypedArray())
        )
      }
      is AddAllowedMembersAction.NavigateToSchedule -> {
        findNavController().safeNavigate(AddAllowedMembersFragmentDirections.actionAddAllowedMembersFragmentToEditNotificationProfileScheduleFragment(action.profileId, true))
      }
    }
  }
}
