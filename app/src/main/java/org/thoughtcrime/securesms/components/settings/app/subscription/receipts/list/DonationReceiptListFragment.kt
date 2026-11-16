package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Lists every donation receipt, split by kind of donation, each opening its receipt.
 */
class DonationReceiptListFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  @Composable
  override fun FragmentContent() {
    DonationReceiptListEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      is AppSettingsRoute.DonationsRoute.Receipt -> findNavController().safeNavigate(
        DonationReceiptListFragmentDirections.actionDonationReceiptListFragmentToDonationReceiptDetailFragment(route.id)
      )
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}

/**
 * The donation receipt list, wired to its view model, navigating through [parentEventEmitter].
 */
@Composable
fun DonationReceiptListEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: DonationReceiptListViewModel = viewModel { DonationReceiptListViewModel() }
  val state by viewModel.state.collectAsStateWithLifecycle()

  CollectActions(viewModel.actions) { action ->
    when (action) {
      DonationReceiptListAction.NavigateBack -> parentEventEmitter.navigateBack()
      is DonationReceiptListAction.NavigateToReceipt -> parentEventEmitter.navigateTo(AppSettingsRoute.DonationsRoute.Receipt(action.receiptId))
    }
  }

  DonationReceiptListScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
