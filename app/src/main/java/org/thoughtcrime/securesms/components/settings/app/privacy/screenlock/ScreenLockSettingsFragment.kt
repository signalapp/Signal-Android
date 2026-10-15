package org.thoughtcrime.securesms.components.settings.app.privacy.screenlock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.appsettings.screenlock.ScreenLockSettingsAction
import org.signal.appsettings.screenlock.ScreenLockSettingsEvent
import org.signal.appsettings.screenlock.ScreenLockSettingsScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment

/**
 * Lets the user turn on Signal's screen lock and choose how long the app can be in the background before it locks.
 */
class ScreenLockSettingsFragment : ComposeFragment() {

  private val viewModel: ScreenLockSettingsViewModel by viewModels()

  override fun onResume() {
    super.onResume()
    viewModel.onEvent(ScreenLockSettingsEvent.ScreenResumed)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    ScreenLockSettingsScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: ScreenLockSettingsAction) {
    when (action) {
      ScreenLockSettingsAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
    }
  }
}
