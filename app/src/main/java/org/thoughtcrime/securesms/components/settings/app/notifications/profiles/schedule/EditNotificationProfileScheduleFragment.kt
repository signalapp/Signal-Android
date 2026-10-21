package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.viewModel
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Can edit existing or use during create flow to setup a profile schedule.
 */
class EditNotificationProfileScheduleFragment : ComposeFragment() {

  private val viewModel: EditNotificationProfileScheduleViewModel by viewModel {
    val args = EditNotificationProfileScheduleFragmentArgs.fromBundle(requireArguments())
    EditNotificationProfileScheduleViewModel(profileId = args.profileId, createMode = args.createMode)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    EditNotificationProfileScheduleScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: EditNotificationProfileScheduleAction) {
    when (action) {
      EditNotificationProfileScheduleAction.NavigateBack -> findNavController().navigateUp()
      is EditNotificationProfileScheduleAction.NavigateToProfileCreated -> {
        findNavController().safeNavigate(EditNotificationProfileScheduleFragmentDirections.actionEditNotificationProfileScheduleFragmentToNotificationProfileCreatedFragment(action.profileId))
      }
    }
  }
}
