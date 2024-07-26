/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.activity.OnBackPressedCallback
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.processors.PublishProcessor
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentCheckoutDelegate
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorAction
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.Nav
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.viewModel

/**
 * Handles the selection, payment, and changing of a user's backup tier.
 */
class MessageBackupsFlowFragment : ComposeFragment(), InAppPaymentCheckoutDelegate.Callback {

  private val viewModel: MessageBackupsFlowViewModel by viewModel { MessageBackupsFlowViewModel() }

  private val inAppPaymentIdProcessor = PublishProcessor.create<InAppPaymentTable.InAppPaymentId>()

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun FragmentContent() {
    val state by viewModel.stateFlow.collectAsState()
    val pin by viewModel.pinState
    val navController = rememberNavController()

    val checkoutDelegate = remember {
      InAppPaymentCheckoutDelegate(this, this, inAppPaymentIdProcessor)
    }

    LaunchedEffect(state.inAppPayment?.id) {
      val inAppPaymentId = state.inAppPayment?.id
      if (inAppPaymentId != null) {
        inAppPaymentIdProcessor.onNext(inAppPaymentId)
      }
    }

    val checkoutSheetState = rememberModalBottomSheetState(
      skipPartiallyExpanded = true
    )

    LaunchedEffect(Unit) {
      navController.setLifecycleOwner(this@MessageBackupsFlowFragment)

      requireActivity().onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          viewModel.goToPreviousScreen()
        }
      })
    }

    Nav.Host(
      navController = navController,
      startDestination = state.startScreen.name
    ) {
      composable(route = MessageBackupsScreen.EDUCATION.name) {
        MessageBackupsEducationScreen(
          onNavigationClick = viewModel::goToPreviousScreen,
          onEnableBackups = viewModel::goToNextScreen,
          onLearnMore = {}
        )
      }

      composable(route = MessageBackupsScreen.PIN_EDUCATION.name) {
        MessageBackupsPinEducationScreen(
          onNavigationClick = viewModel::goToPreviousScreen,
          onCreatePinClick = {},
          onUseCurrentPinClick = viewModel::goToNextScreen,
          recommendedPinSize = 16 // TODO [message-backups] This value should come from some kind of config
        )
      }

      composable(route = MessageBackupsScreen.PIN_CONFIRMATION.name) {
        MessageBackupsPinConfirmationScreen(
          pin = pin,
          onPinChanged = viewModel::onPinEntryUpdated,
          pinKeyboardType = state.pinKeyboardType,
          onPinKeyboardTypeSelected = viewModel::onPinKeyboardTypeUpdated,
          onNextClick = viewModel::goToNextScreen
        )
      }

      composable(route = MessageBackupsScreen.TYPE_SELECTION.name) {
        MessageBackupsTypeSelectionScreen(
          currentBackupTier = state.currentMessageBackupTier,
          selectedBackupTier = state.selectedMessageBackupTier,
          availableBackupTypes = state.availableBackupTypes,
          onMessageBackupsTierSelected = viewModel::onMessageBackupTierUpdated,
          onNavigationClick = viewModel::goToPreviousScreen,
          onReadMoreClicked = {},
          onCancelSubscriptionClicked = viewModel::displayCancellationDialog,
          onNextClicked = viewModel::goToNextScreen
        )

        if (state.screen == MessageBackupsScreen.CHECKOUT_SHEET) {
          MessageBackupsCheckoutSheet(
            messageBackupsType = state.availableBackupTypes.first { it.tier == state.selectedMessageBackupTier!! },
            availablePaymentMethods = state.availablePaymentMethods,
            sheetState = checkoutSheetState,
            onDismissRequest = {
              viewModel.goToPreviousScreen()
            },
            onPaymentMethodSelected = {
              viewModel.onPaymentMethodUpdated(it)
              viewModel.goToNextScreen()
            }
          )
        }

        if (state.screen == MessageBackupsScreen.CANCELLATION_DIALOG) {
          ConfirmBackupCancellationDialog(
            onConfirmAndDownloadNow = {
              // TODO [message-backups] Set appropriate state to handle post-cancellation action.
              viewModel.goToNextScreen()
            },
            onConfirmAndDownloadLater = {
              // TODO [message-backups] Set appropriate state to handle post-cancellation action.
              viewModel.goToNextScreen()
            },
            onKeepSubscriptionClick = viewModel::goToPreviousScreen
          )
        }
      }
    }

    LaunchedEffect(state.screen) {
      val route = navController.currentDestination?.route ?: return@LaunchedEffect
      if (route == state.screen.name) {
        return@LaunchedEffect
      }

      if (state.screen == MessageBackupsScreen.COMPLETED) {
        if (!findNavController().popBackStack()) {
          requireActivity().finishAfterTransition()
        }
        return@LaunchedEffect
      }

      if (state.screen == MessageBackupsScreen.CREATING_IN_APP_PAYMENT) {
        return@LaunchedEffect
      }

      if (state.screen == MessageBackupsScreen.PROCESS_PAYMENT) {
        checkoutDelegate.handleGatewaySelectionResponse(state.inAppPayment!!)
        viewModel.goToPreviousScreen()
        return@LaunchedEffect
      }

      if (state.screen == MessageBackupsScreen.PROCESS_CANCELLATION) {
        cancelSubscription()
        viewModel.goToPreviousScreen()
        return@LaunchedEffect
      }

      if (state.screen == MessageBackupsScreen.CHECKOUT_SHEET) {
        return@LaunchedEffect
      }

      if (state.screen == MessageBackupsScreen.CANCELLATION_DIALOG) {
        return@LaunchedEffect
      }

      val routeScreen = MessageBackupsScreen.valueOf(route)
      if (routeScreen.isAfter(state.screen)) {
        navController.popBackStack()
      } else {
        navController.navigate(state.screen.name)
      }
    }
  }

  private fun cancelSubscription() {
    findNavController().safeNavigate(
      MessageBackupsFlowFragmentDirections.actionDonateToSignalFragmentToStripePaymentInProgressFragment(
        InAppPaymentProcessorAction.CANCEL_SUBSCRIPTION,
        null,
        InAppPaymentType.RECURRING_BACKUP
      )
    )
  }

  override fun navigateToStripePaymentInProgress(inAppPayment: InAppPaymentTable.InAppPayment) {
    findNavController().safeNavigate(
      MessageBackupsFlowFragmentDirections.actionDonateToSignalFragmentToStripePaymentInProgressFragment(
        InAppPaymentProcessorAction.PROCESS_NEW_IN_APP_PAYMENT,
        inAppPayment,
        inAppPayment.type
      )
    )
  }

  override fun navigateToPayPalPaymentInProgress(inAppPayment: InAppPaymentTable.InAppPayment) {
    findNavController().safeNavigate(
      MessageBackupsFlowFragmentDirections.actionDonateToSignalFragmentToPaypalPaymentInProgressFragment(
        InAppPaymentProcessorAction.PROCESS_NEW_IN_APP_PAYMENT,
        inAppPayment,
        inAppPayment.type
      )
    )
  }

  override fun navigateToCreditCardForm(inAppPayment: InAppPaymentTable.InAppPayment) {
    findNavController().safeNavigate(
      MessageBackupsFlowFragmentDirections.actionDonateToSignalFragmentToCreditCardFragment(inAppPayment)
    )
  }

  override fun navigateToIdealDetailsFragment(inAppPayment: InAppPaymentTable.InAppPayment) {
    findNavController().safeNavigate(
      MessageBackupsFlowFragmentDirections.actionDonateToSignalFragmentToIdealTransferDetailsFragment(inAppPayment)
    )
  }

  override fun navigateToBankTransferMandate(inAppPayment: InAppPaymentTable.InAppPayment) {
    findNavController().safeNavigate(
      MessageBackupsFlowFragmentDirections.actionDonateToSignalFragmentToBankTransferMandateFragment(inAppPayment)
    )
  }

  override fun onPaymentComplete(inAppPayment: InAppPaymentTable.InAppPayment) {
    // TODO [message-backups] What do? probably some kind of success thing?
    if (!findNavController().popBackStack()) {
      requireActivity().finishAfterTransition()
    }
  }

  override fun onSubscriptionCancelled(inAppPaymentType: InAppPaymentType) {
    viewModel.onCancellationComplete()

    if (!findNavController().popBackStack()) {
      requireActivity().finishAfterTransition()
    }
  }

  override fun onProcessorActionProcessed() = Unit

  override fun onUserLaunchedAnExternalApplication() {
    // TODO [message-backups] What do? Are we even supporting bank transfers?
  }

  override fun navigateToDonationPending(inAppPayment: InAppPaymentTable.InAppPayment) {
    // TODO [message-backups] What do? Are we even supporting bank transfers?
  }
}
