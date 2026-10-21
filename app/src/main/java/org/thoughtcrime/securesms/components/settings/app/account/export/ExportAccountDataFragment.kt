package org.thoughtcrime.securesms.components.settings.app.account.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.app.ShareCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.appsettings.exportaccountdata.ExportAccountDataAction
import org.signal.appsettings.exportaccountdata.ExportAccountDataScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.CommunicationActions

class ExportAccountDataFragment : ComposeFragment() {

  companion object {
    val TAG = Log.tag(ExportAccountDataFragment::class.java)
  }

  private val viewModel: ExportAccountDataViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    ExportAccountDataScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: ExportAccountDataAction) {
    when (action) {
      ExportAccountDataAction.NavigateBack -> {
        requireActivity().onBackPressedDispatcher.onBackPressed()
      }

      is ExportAccountDataAction.OpenBrowserLink -> {
        CommunicationActions.openBrowserLink(requireContext(), action.url)
      }

      is ExportAccountDataAction.ShareGeneratedReport -> {
        ShareCompat.IntentBuilder(requireContext())
          .setStream(action.uri)
          .setType(action.mimeType)
          .startChooser()
      }
    }
  }
}
