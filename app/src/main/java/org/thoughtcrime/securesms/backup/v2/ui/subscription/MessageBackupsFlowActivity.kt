/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.util.viewModel

class MessageBackupsFlowActivity : PassphraseRequiredActivity() {

  private val viewModel: MessageBackupsFlowViewModel by viewModel { MessageBackupsFlowViewModel() }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    setContent {
      SignalTheme {
        val state by viewModel.state
        val navController = rememberNavController()

        fun MessageBackupsScreen.next() {
          val nextScreen = viewModel.goToNextScreen(this)
          if (nextScreen == MessageBackupsScreen.COMPLETED) {
            finishAfterTransition()
            return
          }
          if (nextScreen != this) {
            navController.navigate(nextScreen.name)
          }
        }

        fun NavController.popOrFinish() {
          if (popBackStack()) {
            return
          }

          finishAfterTransition()
        }

        LaunchedEffect(Unit) {
          navController.setLifecycleOwner(this@MessageBackupsFlowActivity)
          navController.setOnBackPressedDispatcher(this@MessageBackupsFlowActivity.onBackPressedDispatcher)
          navController.enableOnBackPressed(true)
        }

        NavHost(
          navController = navController,
          startDestination = if (state.currentMessageBackupTier == null) MessageBackupsScreen.EDUCATION.name else MessageBackupsScreen.TYPE_SELECTION.name,
          enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
          exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
          popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
          popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
        ) {
          composable(route = MessageBackupsScreen.EDUCATION.name) {
            MessageBackupsEducationScreen(
              onNavigationClick = navController::popOrFinish,
              onEnableBackups = { MessageBackupsScreen.EDUCATION.next() },
              onLearnMore = {}
            )
          }

          composable(route = MessageBackupsScreen.PIN_EDUCATION.name) {
            MessageBackupsPinEducationScreen(
              onNavigationClick = navController::popOrFinish,
              onGeneratePinClick = {},
              onUseCurrentPinClick = { MessageBackupsScreen.PIN_EDUCATION.next() },
              recommendedPinSize = 16 // TODO [message-backups] This value should come from some kind of config
            )
          }

          composable(route = MessageBackupsScreen.PIN_CONFIRMATION.name) {
            MessageBackupsPinConfirmationScreen(
              pin = state.pin,
              onPinChanged = viewModel::onPinEntryUpdated,
              pinKeyboardType = state.pinKeyboardType,
              onPinKeyboardTypeSelected = viewModel::onPinKeyboardTypeUpdated,
              onNextClick = { MessageBackupsScreen.PIN_CONFIRMATION.next() }
            )
          }

          composable(route = MessageBackupsScreen.TYPE_SELECTION.name) {
            MessageBackupsTypeSelectionScreen(
              selectedBackupTier = state.selectedMessageBackupTier,
              availableBackupTiers = state.availableBackupTiers,
              onMessageBackupsTierSelected = viewModel::onMessageBackupTierUpdated,
              onNavigationClick = navController::popOrFinish,
              onReadMoreClicked = {},
              onNextClicked = { MessageBackupsScreen.TYPE_SELECTION.next() }
            )
          }

          dialog(route = MessageBackupsScreen.CHECKOUT_SHEET.name) {
            MessageBackupsCheckoutSheet(
              messageBackupTier = state.selectedMessageBackupTier!!,
              availablePaymentGateways = state.availablePaymentGateways,
              onDismissRequest = navController::popOrFinish,
              onPaymentGatewaySelected = {
                viewModel.onPaymentGatewayUpdated(it)
                MessageBackupsScreen.CHECKOUT_SHEET.next()
              }
            )
          }
        }
      }
    }
  }
}
