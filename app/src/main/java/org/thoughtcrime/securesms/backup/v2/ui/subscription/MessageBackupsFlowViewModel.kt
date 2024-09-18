/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatValue
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import java.math.BigDecimal

class MessageBackupsFlowViewModel : ViewModel() {

  private val internalStateFlow = MutableStateFlow(
    MessageBackupsFlowState(
      availableBackupTypes = emptyList(),
      selectedMessageBackupTier = SignalStore.backup.backupTier,
      startScreen = if (SignalStore.backup.backupTier == null) MessageBackupsStage.EDUCATION else MessageBackupsStage.TYPE_SELECTION
    )
  )

  val stateFlow: StateFlow<MessageBackupsFlowState> = internalStateFlow

  init {
    check(SignalStore.backup.backupTier != MessageBackupTier.PAID) { "This screen does not support cancellation or downgrades." }

    viewModelScope.launch {
      internalStateFlow.update {
        it.copy(
          availableBackupTypes = BackupRepository.getAvailableBackupsTypes(
            if (!RemoteConfig.messageBackups) emptyList() else listOf(MessageBackupTier.FREE, MessageBackupTier.PAID)
          )
        )
      }
    }

    viewModelScope.launch {
      AppDependencies.billingApi.getBillingPurchaseResults().collect {
        when (it) {
          is BillingPurchaseResult.Success -> {
            // 1. Copy the purchaseToken into our inAppPaymentData
            // 2. Enqueue the redemption chain
            goToNextStage()
          }

          else -> goToPreviousStage()
        }
      }
    }
  }

  /**
   * Go to the next stage of the pipeline, based off of the current stage and state data.
   */
  fun goToNextStage() {
    internalStateFlow.update {
      when (it.stage) {
        MessageBackupsStage.EDUCATION -> it.copy(stage = MessageBackupsStage.BACKUP_KEY_EDUCATION)
        MessageBackupsStage.BACKUP_KEY_EDUCATION -> it.copy(stage = MessageBackupsStage.BACKUP_KEY_RECORD)
        MessageBackupsStage.BACKUP_KEY_RECORD -> it.copy(stage = MessageBackupsStage.TYPE_SELECTION)
        MessageBackupsStage.TYPE_SELECTION -> validateTypeAndUpdateState(it)
        MessageBackupsStage.CHECKOUT_SHEET -> validateGatewayAndUpdateState(it)
        MessageBackupsStage.CREATING_IN_APP_PAYMENT -> error("This is driven by an async coroutine.")
        MessageBackupsStage.PROCESS_PAYMENT -> it.copy(stage = MessageBackupsStage.COMPLETED)
        MessageBackupsStage.PROCESS_FREE -> it.copy(stage = MessageBackupsStage.COMPLETED)
        MessageBackupsStage.COMPLETED -> error("Unsupported state transition from terminal state COMPLETED")
      }
    }
  }

  fun goToPreviousStage() {
    internalStateFlow.update {
      if (it.stage == it.startScreen) {
        it.copy(stage = MessageBackupsStage.COMPLETED)
      } else {
        val previousScreen = when (it.stage) {
          MessageBackupsStage.EDUCATION -> MessageBackupsStage.COMPLETED
          MessageBackupsStage.BACKUP_KEY_EDUCATION -> MessageBackupsStage.EDUCATION
          MessageBackupsStage.BACKUP_KEY_RECORD -> MessageBackupsStage.BACKUP_KEY_EDUCATION
          MessageBackupsStage.TYPE_SELECTION -> MessageBackupsStage.BACKUP_KEY_RECORD
          MessageBackupsStage.CHECKOUT_SHEET -> MessageBackupsStage.TYPE_SELECTION
          MessageBackupsStage.CREATING_IN_APP_PAYMENT -> MessageBackupsStage.CREATING_IN_APP_PAYMENT
          MessageBackupsStage.PROCESS_PAYMENT -> MessageBackupsStage.PROCESS_PAYMENT
          MessageBackupsStage.PROCESS_FREE -> MessageBackupsStage.PROCESS_FREE
          MessageBackupsStage.COMPLETED -> error("Unsupported state transition from terminal state COMPLETED")
        }

        it.copy(stage = previousScreen)
      }
    }
  }

  fun onMessageBackupTierUpdated(messageBackupTier: MessageBackupTier, messageBackupTierLabel: String) {
    internalStateFlow.update {
      it.copy(
        selectedMessageBackupTier = messageBackupTier,
        selectedMessageBackupTierLabel = messageBackupTierLabel
      )
    }
  }

  private fun validateTypeAndUpdateState(state: MessageBackupsFlowState): MessageBackupsFlowState {
    return when (state.selectedMessageBackupTier!!) {
      MessageBackupTier.FREE -> {
        SignalStore.backup.areBackupsEnabled = true
        SignalStore.backup.backupTier = MessageBackupTier.FREE

        state.copy(stage = MessageBackupsStage.PROCESS_FREE)
      }

      MessageBackupTier.PAID -> state.copy(stage = MessageBackupsStage.CHECKOUT_SHEET)
    }
  }

  private fun validateGatewayAndUpdateState(state: MessageBackupsFlowState): MessageBackupsFlowState {
    val backupsType = state.availableBackupTypes.first { it.tier == state.selectedMessageBackupTier }

    viewModelScope.launch(Dispatchers.IO) {
      withContext(Dispatchers.Main) {
        internalStateFlow.update { it.copy(inAppPayment = null) }
      }

      val paidFiat = AppDependencies.billingApi.queryProduct()!!.price

      SignalDatabase.inAppPayments.clearCreated()
      val id = SignalDatabase.inAppPayments.insert(
        type = InAppPaymentType.RECURRING_BACKUP,
        state = InAppPaymentTable.State.CREATED,
        subscriberId = null,
        endOfPeriod = null,
        inAppPaymentData = InAppPaymentData(
          badge = null,
          label = state.selectedMessageBackupTierLabel!!,
          amount = if (backupsType is MessageBackupsType.Paid) paidFiat.toFiatValue() else FiatMoney(BigDecimal.ZERO, paidFiat.currency).toFiatValue(),
          level = SubscriptionsConfiguration.BACKUPS_LEVEL.toLong(),
          recipientId = Recipient.self().id.serialize(),
          paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
          redemption = InAppPaymentData.RedemptionState(
            stage = InAppPaymentData.RedemptionState.Stage.INIT
          )
        )
      )

      val inAppPayment = SignalDatabase.inAppPayments.getById(id)!!

      withContext(Dispatchers.Main) {
        internalStateFlow.update { it.copy(inAppPayment = inAppPayment, stage = MessageBackupsStage.PROCESS_PAYMENT) }
      }
    }

    return state.copy(stage = MessageBackupsStage.CREATING_IN_APP_PAYMENT)
  }
}
