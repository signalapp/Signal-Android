/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.rx3.asFlowable
import org.signal.core.ui.Dialogs
import org.signal.core.util.getSerializableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentCheckoutDelegate
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.Nav
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.viewModel

/**
 * Handles the selection, payment, and changing of a user's backup tier.
 */
class MessageBackupsFlowFragment : ComposeFragment(), InAppPaymentCheckoutDelegate.ErrorHandlerCallback {

  companion object {

    @VisibleForTesting
    const val TIER = "tier"

    fun create(messageBackupTier: MessageBackupTier?): MessageBackupsFlowFragment {
      return MessageBackupsFlowFragment().apply {
        arguments = bundleOf(TIER to messageBackupTier)
      }
    }
  }

  private val viewModel: MessageBackupsFlowViewModel by viewModel {
    MessageBackupsFlowViewModel(requireArguments().getSerializableCompat(TIER, MessageBackupTier::class.java))
  }

  private val errorHandler = InAppPaymentCheckoutDelegate.ErrorHandler()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    errorHandler.attach(
      fragment = this,
      errorHandlerCallback = this,
      inAppPaymentIdSource = viewModel.stateFlow.asFlowable()
        .filter { it.inAppPayment != null }
        .map { it.inAppPayment!!.id }
    )
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
      navController.setLifecycleOwner(this@MessageBackupsFlowFragment)

      requireActivity().onBackPressedDispatcher.addCallback(
        lifecycleOwner,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            viewModel.goToPreviousStage()
          }
        }
      )
    }

    Nav.Host(
      navController = navController,
      startDestination = state.startScreen.name
    ) {
      composable(route = MessageBackupsStage.Route.EDUCATION.name) {
        MessageBackupsEducationScreen(
          onNavigationClick = viewModel::goToPreviousStage,
          onEnableBackups = viewModel::goToNextStage,
          onLearnMore = {
            CommunicationActions.openBrowserLink(requireContext(), getString(R.string.backup_support_url))
          }
        )
      }

      composable(route = MessageBackupsStage.Route.BACKUP_KEY_EDUCATION.name) {
        MessageBackupsKeyEducationScreen(
          onNavigationClick = viewModel::goToPreviousStage,
          onNextClick = viewModel::goToNextStage
        )
      }

      composable(route = MessageBackupsStage.Route.BACKUP_KEY_RECORD.name) {
        val context = LocalContext.current

        MessageBackupsKeyRecordScreen(
          backupKey = state.accountEntropyPool.value,
          onNavigationClick = viewModel::goToPreviousStage,
          onNextClick = viewModel::goToNextStage,
          onCopyToClipboardClick = {
            Util.copyToClipboard(context, it)
          }
        )
      }

      composable(route = MessageBackupsStage.Route.TYPE_SELECTION.name) {
        MessageBackupsTypeSelectionScreen(
          stage = state.stage,
          paymentReadyState = state.paymentReadyState,
          currentBackupTier = state.currentMessageBackupTier,
          selectedBackupTier = state.selectedMessageBackupTier,
          availableBackupTypes = state.availableBackupTypes,
          onMessageBackupsTierSelected = viewModel::onMessageBackupTierUpdated,
          onNavigationClick = viewModel::goToPreviousStage,
          onReadMoreClicked = {},
          onNextClicked = viewModel::goToNextStage
        )
      }
    }

    LaunchedEffect(state.stage) {
      val newRoute = state.stage.route.name
      val currentRoute = navController.currentDestination?.route
      if (currentRoute != newRoute) {
        if (currentRoute != null && MessageBackupsStage.Route.valueOf(currentRoute).isAfter(state.stage.route)) {
          navController.popBackStack()
        } else {
          navController.navigate(newRoute)
        }
      }

      if (state.stage == MessageBackupsStage.CHECKOUT_SHEET) {
        AppDependencies.billingApi.launchBillingFlow(requireActivity())
      }

      if (state.stage == MessageBackupsStage.COMPLETED) {
        requireActivity().setResult(Activity.RESULT_OK, MessageBackupsCheckoutActivity.createResultData())
        requireActivity().finishAfterTransition()
      }
    }

    if (state.paymentReadyState == MessageBackupsFlowState.PaymentReadyState.FAILED) {
      Dialogs.SimpleMessageDialog(
        message = stringResource(R.string.MessageBackupsFlowFragment__a_network_failure_occurred),
        dismiss = stringResource(android.R.string.ok),
        onDismiss = { requireActivity().finishAfterTransition() }
      )
    }
  }

  override fun onUserLaunchedAnExternalApplication() = error("Not supported by this fragment.")

  override fun navigateToDonationPending(inAppPayment: InAppPaymentTable.InAppPayment) = error("Not supported by this fragment.")
  override fun exitCheckoutFlow() {
    requireActivity().finishAfterTransition()
  }
}
