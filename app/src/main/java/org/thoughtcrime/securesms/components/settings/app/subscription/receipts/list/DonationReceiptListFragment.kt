package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.viewModel
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Lists every donation receipt, split by kind of donation, each opening its receipt.
 */
class DonationReceiptListFragment : ComposeFragment() {

  private val viewModel: DonationReceiptListViewModel by viewModel { DonationReceiptListViewModel() }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    DonationReceiptListScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: DonationReceiptListAction) {
    when (action) {
      DonationReceiptListAction.NavigateBack -> findNavController().popBackStack()
      is DonationReceiptListAction.NavigateToReceipt -> {
        findNavController().safeNavigate(DonationReceiptListFragmentDirections.actionDonationReceiptListFragmentToDonationReceiptDetailFragment(action.receiptId))
      }
    }
  }
}
