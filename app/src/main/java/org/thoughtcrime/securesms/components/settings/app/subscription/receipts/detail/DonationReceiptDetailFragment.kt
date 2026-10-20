package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.viewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.receipts.ReceiptImageRenderer

class DonationReceiptDetailFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(DonationReceiptDetailFragment::class)
  }

  private val viewModel: DonationReceiptDetailViewModel by viewModel {
    val id = DonationReceiptDetailFragmentArgs.fromBundle(requireArguments()).id
    DonationReceiptDetailViewModel(id)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    DonationReceiptDetailScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: DonationReceiptDetailAction) {
    when (action) {
      DonationReceiptDetailAction.NavigateBack -> findNavController().popBackStack()
      is DonationReceiptDetailAction.ShareReceipt -> {
        try {
          startActivity(ReceiptImageRenderer.createShareIntent(requireContext(), action.uri))
        } catch (e: ActivityNotFoundException) {
          Log.w(TAG, "No activity existed to share the media.", e)
          Toast.makeText(requireContext(), R.string.MediaPreviewActivity_cant_find_an_app_able_to_share_this_media, Toast.LENGTH_LONG).show()
        }
      }
    }
  }
}
