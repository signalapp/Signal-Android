/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.text.TextUtils
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatValue
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppDonations
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayOrderStrategy
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.lock.v2.SvrConstants
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.kbs.PinHashUtil.verifyLocalPinHash
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration

class MessageBackupsFlowViewModel : ViewModel() {
  private val internalState = mutableStateOf(
    MessageBackupsFlowState(
      availableBackupTypes = emptyList(),
      selectedMessageBackupTier = SignalStore.backup.backupTier,
      availablePaymentMethods = GatewayOrderStrategy.getStrategy().orderedGateways.filter { InAppDonations.isPaymentSourceAvailable(it.toPaymentSourceType(), InAppPaymentType.RECURRING_BACKUP) },
      startScreen = if (SignalStore.backup.backupTier == null) MessageBackupsScreen.EDUCATION else MessageBackupsScreen.TYPE_SELECTION
    )
  )

  val state: State<MessageBackupsFlowState> = internalState

  init {
    viewModelScope.launch {
      internalState.value = internalState.value.copy(
        availableBackupTypes = BackupRepository.getAvailableBackupsTypes(
          if (!RemoteConfig.messageBackups) emptyList() else listOf(MessageBackupTier.FREE, MessageBackupTier.PAID)
        )
      )
    }
  }

  fun goToNextScreen() {
    val nextScreen = when (internalState.value.screen) {
      MessageBackupsScreen.EDUCATION -> MessageBackupsScreen.PIN_EDUCATION
      MessageBackupsScreen.PIN_EDUCATION -> MessageBackupsScreen.PIN_CONFIRMATION
      MessageBackupsScreen.PIN_CONFIRMATION -> validatePinAndUpdateState()
      MessageBackupsScreen.TYPE_SELECTION -> validateTypeAndUpdateState()
      MessageBackupsScreen.CHECKOUT_SHEET -> validateGatewayAndUpdateState()
      MessageBackupsScreen.PROCESS_PAYMENT -> MessageBackupsScreen.COMPLETED
      MessageBackupsScreen.COMPLETED -> error("Unsupported state transition from terminal state COMPLETED")
    }

    internalState.value = state.value.copy(screen = nextScreen)
  }

  fun goToPreviousScreen() {
    if (internalState.value.screen == internalState.value.startScreen) {
      internalState.value = state.value.copy(screen = MessageBackupsScreen.COMPLETED)
      return
    }

    val previousScreen = when (internalState.value.screen) {
      MessageBackupsScreen.EDUCATION -> MessageBackupsScreen.COMPLETED
      MessageBackupsScreen.PIN_EDUCATION -> MessageBackupsScreen.EDUCATION
      MessageBackupsScreen.PIN_CONFIRMATION -> MessageBackupsScreen.PIN_EDUCATION
      MessageBackupsScreen.TYPE_SELECTION -> MessageBackupsScreen.PIN_CONFIRMATION
      MessageBackupsScreen.CHECKOUT_SHEET -> MessageBackupsScreen.TYPE_SELECTION
      MessageBackupsScreen.PROCESS_PAYMENT -> MessageBackupsScreen.TYPE_SELECTION
      MessageBackupsScreen.COMPLETED -> error("Unsupported state transition from terminal state COMPLETED")
    }

    internalState.value = state.value.copy(screen = previousScreen)
  }

  fun onPinEntryUpdated(pin: String) {
    internalState.value = state.value.copy(pin = pin)
  }

  fun onPinKeyboardTypeUpdated(pinKeyboardType: PinKeyboardType) {
    internalState.value = state.value.copy(pinKeyboardType = pinKeyboardType)
  }

  fun onPaymentMethodUpdated(paymentMethod: InAppPaymentData.PaymentMethodType) {
    internalState.value = state.value.copy(selectedPaymentMethod = paymentMethod)
  }

  fun onMessageBackupTierUpdated(messageBackupTier: MessageBackupTier) {
    internalState.value = state.value.copy(selectedMessageBackupTier = messageBackupTier)
  }

  private fun validatePinAndUpdateState(): MessageBackupsScreen {
    val pinHash = SignalStore.svr.localPinHash
    val pin = state.value.pin

    if (pinHash == null || TextUtils.isEmpty(pin) || pin.length < SvrConstants.MINIMUM_PIN_LENGTH) return MessageBackupsScreen.PIN_CONFIRMATION

    if (!verifyLocalPinHash(pinHash, pin)) {
      return MessageBackupsScreen.PIN_CONFIRMATION
    }
    return MessageBackupsScreen.TYPE_SELECTION
  }

  private fun validateTypeAndUpdateState(): MessageBackupsScreen {
    SignalStore.backup.areBackupsEnabled = true
    SignalStore.backup.backupTier = state.value.selectedMessageBackupTier!!

    // TODO [message-backups] - Does anything need to be kicked off?

    return when (state.value.selectedMessageBackupTier!!) {
      MessageBackupTier.FREE -> MessageBackupsScreen.COMPLETED
      MessageBackupTier.PAID -> MessageBackupsScreen.CHECKOUT_SHEET
    }
  }

  private fun validateGatewayAndUpdateState(): MessageBackupsScreen {
    val stateSnapshot = state.value
    val backupsType = stateSnapshot.availableBackupTypes.first { it.tier == stateSnapshot.selectedMessageBackupTier }

    internalState.value = state.value.copy(inAppPayment = null)

    viewModelScope.launch(Dispatchers.IO) {
      SignalDatabase.inAppPayments.clearCreated()
      val id = SignalDatabase.inAppPayments.insert(
        type = InAppPaymentType.RECURRING_BACKUP,
        state = InAppPaymentTable.State.CREATED,
        subscriberId = null,
        endOfPeriod = null,
        inAppPaymentData = InAppPaymentData(
          badge = null,
          label = backupsType.title,
          amount = backupsType.pricePerMonth.toFiatValue(),
          level = SubscriptionsConfiguration.BACKUPS_LEVEL.toLong(),
          recipientId = Recipient.self().id.serialize(),
          paymentMethodType = stateSnapshot.selectedPaymentMethod!!,
          redemption = InAppPaymentData.RedemptionState(
            stage = InAppPaymentData.RedemptionState.Stage.INIT
          )
        )
      )

      val inAppPayment = SignalDatabase.inAppPayments.getById(id)!!

      withContext(Dispatchers.Main) {
        internalState.value = state.value.copy(inAppPayment = inAppPayment)
      }
    }

    return MessageBackupsScreen.PROCESS_PAYMENT
  }
}
