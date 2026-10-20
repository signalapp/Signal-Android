package org.thoughtcrime.securesms.components.settings.app.usernamelinks.colorpicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.usernamecolorpicker.UsernameLinkQrColorPickerAction
import org.signal.appsettings.usernamecolorpicker.UsernameLinkQrColorPickerScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment

/**
 * Gives the user the ability to change the color of their shareable username QR code with a live preview.
 */
class UsernameLinkQrColorPickerFragment : ComposeFragment() {

  private val viewModel: UsernameLinkQrColorPickerViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    UsernameLinkQrColorPickerScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: UsernameLinkQrColorPickerAction) {
    when (action) {
      UsernameLinkQrColorPickerAction.NavigateBack -> findNavController().popBackStack()
    }
  }
}
