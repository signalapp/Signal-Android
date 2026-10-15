package org.thoughtcrime.securesms.components.settings.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.dataandstorage.DataAndStorageSettingsAction
import org.signal.appsettings.dataandstorage.DataAndStorageSettingsEvent
import org.signal.appsettings.dataandstorage.DataAndStorageSettingsScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Lets the user control automatic media downloads, sent media quality, call data usage, and network connectivity.
 */
class DataAndStorageSettingsFragment : ComposeFragment() {

  private val viewModel: DataAndStorageSettingsViewModel by viewModels()

  override fun onResume() {
    super.onResume()
    viewModel.onEvent(DataAndStorageSettingsEvent.ScreenResumed)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    DataAndStorageSettingsScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: DataAndStorageSettingsAction) {
    when (action) {
      DataAndStorageSettingsAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
      DataAndStorageSettingsAction.NavigateToManageStorage -> findNavController().safeNavigate(R.id.action_dataAndStorageSettingsFragment_to_storagePreferenceFragment)
      DataAndStorageSettingsAction.NavigateToProxy -> findNavController().safeNavigate(R.id.action_dataAndStorageSettingsFragment_to_editProxyFragment)
    }
  }
}
