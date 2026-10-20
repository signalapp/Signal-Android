package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.notificationprofilecreated.NotificationProfileCreatedAction
import org.signal.appsettings.notificationprofilecreated.NotificationProfileCreatedScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.viewModel
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Shown at the end of the profile create flow.
 */
class NotificationProfileCreatedFragment : ComposeFragment() {

  private val viewModel: NotificationProfileCreatedViewModel by viewModel {
    NotificationProfileCreatedViewModel(NotificationProfileCreatedFragmentArgs.fromBundle(requireArguments()).profileId)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    NotificationProfileCreatedScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: NotificationProfileCreatedAction) {
    when (action) {
      is NotificationProfileCreatedAction.NavigateToProfileDetails -> {
        findNavController().safeNavigate(NotificationProfileCreatedFragmentDirections.actionNotificationProfileCreatedFragmentToNotificationProfileDetailsFragment(action.profileId))
      }
    }
  }
}
