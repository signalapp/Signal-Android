package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.components.settings.app.subscription.receipts.ReceiptImageRenderer

class DonationReceiptDetailFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter()

  @Composable
  override fun FragmentContent() {
    DonationReceiptDetailEntry(
      receiptId = DonationReceiptDetailFragmentArgs.fromBundle(requireArguments()).id,
      parentEventEmitter = appSettingsRouter::onEvent
    )
  }
}

private val TAG = Log.tag(DonationReceiptDetailFragment::class)

/**
 * The receipt [receiptId], wired to its view model and the share sheet, leaving through [parentEventEmitter].
 */
@Composable
fun DonationReceiptDetailEntry(receiptId: Long, parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val context = LocalContext.current
  val viewModel: DonationReceiptDetailViewModel = viewModel { DonationReceiptDetailViewModel(receiptId) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  CollectActions(viewModel.actions) { action ->
    when (action) {
      DonationReceiptDetailAction.NavigateBack -> parentEventEmitter.navigateBack()
      is DonationReceiptDetailAction.ShareReceipt -> {
        try {
          context.startActivity(ReceiptImageRenderer.createShareIntent(context, action.uri))
        } catch (e: ActivityNotFoundException) {
          Log.w(TAG, "No activity existed to share the media.", e)
          Toast.makeText(context, R.string.MediaPreviewActivity_cant_find_an_app_able_to_share_this_media, Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  DonationReceiptDetailScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
