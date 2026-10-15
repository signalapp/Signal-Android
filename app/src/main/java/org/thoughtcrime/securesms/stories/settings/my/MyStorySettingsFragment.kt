package org.thoughtcrime.securesms.stories.settings.my

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.WrapperDialogFragment
import org.thoughtcrime.securesms.stories.settings.connections.ViewAllSignalConnectionsFragment
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Hosts [MyStorySettingsScreen] and carries out the [MyStorySettingsAction]s that need the fragment manager or nav graph.
 */
class MyStorySettingsFragment : ComposeFragment() {

  private val viewModel: MyStorySettingsViewModel by viewModels()

  override fun onResume() {
    super.onResume()
    viewModel.onEvent(MyStorySettingsEvent.ScreenResumed)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    MyStorySettingsScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: MyStorySettingsAction) {
    when (action) {
      MyStorySettingsAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
      MyStorySettingsAction.ShowAllSignalConnections -> ViewAllSignalConnectionsFragment.Dialog.show(childFragmentManager)
      MyStorySettingsAction.NavigateToAllExcept -> findNavController().safeNavigate(R.id.action_myStorySettings_to_allExceptFragment)
      MyStorySettingsAction.NavigateToOnlyShareWith -> findNavController().safeNavigate(R.id.action_myStorySettings_to_onlyShareWithFragment)
      MyStorySettingsAction.ShowSignalConnectionsEducation -> findNavController().safeNavigate(R.id.action_myStorySettings_to_signalConnectionsBottomSheet)
    }
  }

  class Dialog : WrapperDialogFragment() {
    override fun getWrappedFragment(): Fragment {
      return NavHostFragment.create(R.navigation.my_story_settings)
    }
  }

  companion object {
    fun createAsDialog(): DialogFragment {
      return Dialog()
    }
  }
}
