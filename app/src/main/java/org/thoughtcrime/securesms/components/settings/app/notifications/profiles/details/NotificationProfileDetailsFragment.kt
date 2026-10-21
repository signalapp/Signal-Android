package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.viewModel
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Shows a notification profile and lets the user change who it allows, its schedule and exceptions, or delete it.
 */
class NotificationProfileDetailsFragment : ComposeFragment() {

  private val viewModel: NotificationProfileDetailsViewModel by viewModel {
    NotificationProfileDetailsViewModel(NotificationProfileDetailsFragmentArgs.fromBundle(requireArguments()).profileId)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    NotificationProfileDetailsScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: NotificationProfileDetailsAction) {
    when (action) {
      NotificationProfileDetailsAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
      is NotificationProfileDetailsAction.NavigateToEdit -> {
        findNavController().safeNavigate(
          NotificationProfileDetailsFragmentDirections.actionNotificationProfileDetailsFragmentToEditNotificationProfileFragment().setProfileId(action.profileId)
        )
      }
      is NotificationProfileDetailsAction.NavigateToSelectRecipients -> {
        findNavController().safeNavigate(
          NotificationProfileDetailsFragmentDirections.actionNotificationProfileDetailsFragmentToSelectRecipientsFragment(action.profileId)
            .setCurrentSelection(action.currentSelection.toTypedArray())
        )
      }
      is NotificationProfileDetailsAction.NavigateToSchedule -> {
        findNavController().safeNavigate(
          NotificationProfileDetailsFragmentDirections.actionNotificationProfileDetailsFragmentToEditNotificationProfileScheduleFragment(action.profileId, false)
        )
      }
    }
  }
}
