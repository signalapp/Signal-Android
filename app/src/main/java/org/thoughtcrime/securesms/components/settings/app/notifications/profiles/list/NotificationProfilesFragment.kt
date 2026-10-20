package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Primary entry point for Notification Profiles. When user has no profiles, shows empty state, otherwise shows
 * all current profiles.
 */
class NotificationProfilesFragment : ComposeFragment() {

  private val viewModel: NotificationProfilesViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    NotificationProfilesScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: NotificationProfilesAction) {
    when (action) {
      NotificationProfilesAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
      NotificationProfilesAction.NavigateToNewProfile -> findNavController().safeNavigate(R.id.action_notificationProfilesFragment_to_editNotificationProfileFragment)
      is NotificationProfilesAction.NavigateToProfileDetails -> {
        findNavController().safeNavigate(NotificationProfilesFragmentDirections.actionNotificationProfilesFragmentToNotificationProfileDetailsFragment(action.profileId))
      }
    }
  }
}
