/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.text.TextUtils
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayResponse
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.lock.v2.SvrConstants
import org.thoughtcrime.securesms.util.FeatureFlags
import org.whispersystems.signalservice.api.kbs.PinHashUtil.verifyLocalPinHash

class MessageBackupsFlowViewModel : ViewModel() {
  private val internalState = mutableStateOf(
    MessageBackupsFlowState(
      availableBackupTiers = if (!FeatureFlags.messageBackups()) {
        emptyList()
      } else {
        listOf(MessageBackupTier.FREE, MessageBackupTier.PAID)
      },
      selectedMessageBackupTier = SignalStore.backup().backupTier
    )
  )

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

  fun onMessageBackupTierUpdated(messageBackupTier: MessageBackupTier) {
    internalState.value = state.value.copy(selectedMessageBackupTier = messageBackupTier)
  }

  private fun validatePinAndUpdateState(): MessageBackupsScreen {
    val pinHash = SignalStore.svr().localPinHash
    val pin = state.value.pin

    if (pinHash == null || TextUtils.isEmpty(pin) || pin.length < SvrConstants.MINIMUM_PIN_LENGTH) return MessageBackupsScreen.PIN_CONFIRMATION

    if (!verifyLocalPinHash(pinHash, pin)) {
      return MessageBackupsScreen.PIN_CONFIRMATION
    }
    return MessageBackupsScreen.TYPE_SELECTION
  }

  private fun validateTypeAndUpdateState(): MessageBackupsScreen {
    SignalStore.backup().areBackupsEnabled = true
    return MessageBackupsScreen.COMPLETED
    // return MessageBackupsScreen.CHECKOUT_SHEET TODO [message-backups] Switch back to payment flow
  }

  private fun validateGatewayAndUpdateState(): MessageBackupsScreen {
    return MessageBackupsScreen.PROCESS_PAYMENT
  }
}
