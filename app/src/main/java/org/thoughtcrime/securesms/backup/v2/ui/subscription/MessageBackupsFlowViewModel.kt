/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayResponse
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType

class MessageBackupsFlowViewModel : ViewModel() {
  private val internalState = mutableStateOf(MessageBackupsFlowState())

  val state: State<MessageBackupsFlowState> = internalState

  fun goToNextScreen(currentScreen: MessageBackupsScreen): MessageBackupsScreen {
    return when (currentScreen) {
      MessageBackupsScreen.EDUCATION -> MessageBackupsScreen.PIN_EDUCATION
      MessageBackupsScreen.PIN_EDUCATION -> MessageBackupsScreen.PIN_CONFIRMATION
      MessageBackupsScreen.PIN_CONFIRMATION -> validatePinAndUpdateState()
      MessageBackupsScreen.TYPE_SELECTION -> validateTypeAndUpdateState()
      MessageBackupsScreen.CHECKOUT_SHEET -> validateGatewayAndUpdateState()
      MessageBackupsScreen.PROCESS_PAYMENT -> MessageBackupsScreen.COMPLETED
      MessageBackupsScreen.COMPLETED -> error("Unsupported state transition from terminal state COMPLETED")
    }
  }

  fun onPinEntryUpdated(pin: String) {
    internalState.value = state.value.copy(pin = pin)
  }

  fun onPinKeyboardTypeUpdated(pinKeyboardType: PinKeyboardType) {
    internalState.value = state.value.copy(pinKeyboardType = pinKeyboardType)
  }

  fun onPaymentGatewayUpdated(gateway: GatewayResponse.Gateway) {
    internalState.value = state.value.copy(selectedPaymentGateway = gateway)
  }

  fun onMessageBackupsTypeUpdated(messageBackupsType: MessageBackupsType) {
    internalState.value = state.value.copy(selectedMessageBackupsType = messageBackupsType)
  }

  private fun validatePinAndUpdateState(): MessageBackupsScreen {
    return MessageBackupsScreen.TYPE_SELECTION
  }

  private fun validateTypeAndUpdateState(): MessageBackupsScreen {
    return MessageBackupsScreen.CHECKOUT_SHEET
  }

  private fun validateGatewayAndUpdateState(): MessageBackupsScreen {
    return MessageBackupsScreen.PROCESS_PAYMENT
  }
}
