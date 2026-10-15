package org.thoughtcrime.securesms.components.settings.app.privacy.pnp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.appsettings.phonenumberprivacy.PhoneNumberPrivacySettingsAction
import org.signal.appsettings.phonenumberprivacy.PhoneNumberPrivacySettingsScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment

/**
 * Lets the user choose who can see their phone number and who can find them by it.
 */
class PhoneNumberPrivacySettingsFragment : ComposeFragment() {

  private val viewModel: PhoneNumberPrivacySettingsViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    PhoneNumberPrivacySettingsScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: PhoneNumberPrivacySettingsAction) {
    when (action) {
      PhoneNumberPrivacySettingsAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
    }
  }
}
