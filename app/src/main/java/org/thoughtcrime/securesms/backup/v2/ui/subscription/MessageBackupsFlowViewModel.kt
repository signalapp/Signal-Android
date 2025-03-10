/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.annotation.WorkerThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatValue
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.InAppPaymentPurchaseTokenJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.storage.IAPSubscriptionId
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import kotlin.time.Duration.Companion.seconds

class MessageBackupsFlowViewModel(
  initialTierSelection: MessageBackupTier?,
  startScreen: MessageBackupsStage = if (SignalStore.backup.backupTier == null) MessageBackupsStage.EDUCATION else MessageBackupsStage.TYPE_SELECTION
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(MessageBackupsFlowViewModel::class)
  }

  private val internalStateFlow = MutableStateFlow(
    MessageBackupsFlowState(
      availableBackupTypes = emptyList(),
      selectedMessageBackupTier = initialTierSelection ?: SignalStore.backup.backupTier,
      startScreen = startScreen
    )
  )

  val stateFlow: StateFlow<MessageBackupsFlowState> = internalStateFlow

  init {
    check(SignalStore.backup.backupTier != MessageBackupTier.PAID) { "This screen does not support cancellation or downgrades." }

    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        BackupRepository.triggerBackupIdReservation()
      }

      result.runIfSuccessful {
        Log.d(TAG, "Successfully triggered backup id reservation.")
        internalStateFlow.update { it.copy(paymentReadyState = MessageBackupsFlowState.PaymentReadyState.READY) }
      }

      result.runOnStatusCodeError {
        Log.d(TAG, "Failed to trigger backup id reservation. ($it)")
        internalStateFlow.update { it.copy(paymentReadyState = MessageBackupsFlowState.PaymentReadyState.FAILED) }
      }
    }

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
      AppDependencies.billingApi.getBillingPurchaseResults().collect { result ->
        when (result) {
          is BillingPurchaseResult.Success -> {
            Log.d(TAG, "Got successful purchase result for purchase at ${result.purchaseTime}")
            val id = internalStateFlow.value.inAppPayment!!.id

            if (result.isAcknowledged) {
              Log.w(TAG, "Payment is already acknowledged. Ignoring.")

              internalStateFlow.update {
                it.copy(
                  stage = MessageBackupsStage.COMPLETED
                )
              }

              return@collect
            }

            try {
              Log.d(TAG, "Attempting to handle successful purchase.")

              internalStateFlow.update {
                it.copy(
                  stage = MessageBackupsStage.PROCESS_PAYMENT
                )
              }

              handleSuccess(result, id)

              internalStateFlow.update {
                it.copy(
                  stage = MessageBackupsStage.COMPLETED
                )
              }
            } catch (e: Exception) {
              Log.d(TAG, "Failed to handle purchase.", e)
              InAppPaymentsRepository.handlePipelineError(
                inAppPaymentId = id,
                donationErrorSource = DonationErrorSource.BACKUPS,
                paymentSourceType = PaymentSourceType.GooglePlayBilling,
                error = e
              )

              internalStateFlow.update {
                it.copy(
                  stage = MessageBackupsStage.FAILURE,
                  failure = e
                )
              }
            }
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
        MessageBackupsStage.CHECKOUT_SHEET -> it.copy(stage = MessageBackupsStage.PROCESS_PAYMENT)
        MessageBackupsStage.CREATING_IN_APP_PAYMENT -> error("This is driven by an async coroutine.")
        MessageBackupsStage.PROCESS_PAYMENT -> error("This is driven by an async coroutine.")
        MessageBackupsStage.PROCESS_FREE -> error("This is driven by an async coroutine.")
        MessageBackupsStage.COMPLETED -> error("Unsupported state transition from terminal state COMPLETED")
        MessageBackupsStage.FAILURE -> error("Unsupported state transition from terminal state FAILURE")
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
          MessageBackupsStage.FAILURE -> error("Unsupported state transition from terminal state FAILURE")
        }

        it.copy(stage = previousScreen)
      }
    }
  }

  fun onMessageBackupTierUpdated(messageBackupTier: MessageBackupTier) {
    internalStateFlow.update {
      it.copy(
        selectedMessageBackupTier = messageBackupTier
      )
    }
  }

  private fun validateTypeAndUpdateState(state: MessageBackupsFlowState): MessageBackupsFlowState {
    return when (state.selectedMessageBackupTier!!) {
      MessageBackupTier.FREE -> {
        SignalStore.backup.backupTier = MessageBackupTier.FREE
        SignalStore.uiHints.markHasEverEnabledRemoteBackups()

        state.copy(stage = MessageBackupsStage.COMPLETED)
      }

      MessageBackupTier.PAID -> {
        check(state.selectedMessageBackupTier == MessageBackupTier.PAID)
        check(state.availableBackupTypes.any { it.tier == state.selectedMessageBackupTier })

        viewModelScope.launch(Dispatchers.IO) {
          internalStateFlow.update { it.copy(inAppPayment = null) }

          val paidFiat = AppDependencies.billingApi.queryProduct()!!.price

          SignalDatabase.inAppPayments.clearCreated()
          val id = SignalDatabase.inAppPayments.insert(
            type = InAppPaymentType.RECURRING_BACKUP,
            state = InAppPaymentTable.State.CREATED,
            subscriberId = null,
            endOfPeriod = null,
            inAppPaymentData = InAppPaymentData(
              badge = null,
              amount = paidFiat.toFiatValue(),
              level = SubscriptionsConfiguration.BACKUPS_LEVEL.toLong(),
              recipientId = Recipient.self().id.serialize(),
              paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING
            )
          )

          val inAppPayment = SignalDatabase.inAppPayments.getById(id)!!
          internalStateFlow.update {
            it.copy(inAppPayment = inAppPayment, stage = MessageBackupsStage.CHECKOUT_SHEET)
          }
        }

        state.copy(stage = MessageBackupsStage.CREATING_IN_APP_PAYMENT)
      }
    }
  }

  /**
   * Ensures we have a SubscriberId created and available for use. This is considered safe because
   * the screen this is called in is assumed to only be accessible if the user does not currently have
   * a subscription.
   */
  @WorkerThread
  private fun ensureSubscriberIdForBackups(purchaseToken: IAPSubscriptionId.GooglePlayBillingPurchaseToken) {
    RecurringInAppPaymentRepository.ensureSubscriberId(InAppPaymentSubscriberRecord.Type.BACKUP, iapSubscriptionId = purchaseToken).blockingAwait()
  }

  /**
   * Handles a successful BillingPurchaseResult. Updates the in app payment, enqueues the appropriate job chain,
   * and handles any resulting error. Like donations, we will wait up to 10s for the completion of the job chain.
   */
  @OptIn(FlowPreview::class)
  private suspend fun handleSuccess(result: BillingPurchaseResult.Success, inAppPaymentId: InAppPaymentTable.InAppPaymentId) {
    withContext(Dispatchers.IO) {
      Log.d(TAG, "Setting purchase token data on InAppPayment and InAppPaymentSubscriber.")
      ensureSubscriberIdForBackups(IAPSubscriptionId.GooglePlayBillingPurchaseToken(result.purchaseToken))

      val inAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)!!
      SignalDatabase.inAppPayments.update(
        inAppPayment.copy(
          state = InAppPaymentTable.State.PENDING,
          subscriberId = InAppPaymentsRepository.requireSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP).subscriberId,
          data = inAppPayment.data.newBuilder().redemption(
            redemption = InAppPaymentData.RedemptionState(
              stage = InAppPaymentData.RedemptionState.Stage.INIT
            )
          ).build()
        )
      )

      Log.d(TAG, "Enqueueing InAppPaymentPurchaseTokenJob chain.")
      SignalStore.uiHints.markHasEverEnabledRemoteBackups()
      InAppPaymentPurchaseTokenJob.createJobChain(inAppPayment).enqueue()
    }

    val terminalInAppPayment = withContext(Dispatchers.IO) {
      Log.d(TAG, "Awaiting completion of job chain for up to 10 seconds.")
      InAppPaymentsRepository.observeUpdates(inAppPaymentId).asFlow()
        .filter { it.state == InAppPaymentTable.State.END }
        .take(1)
        .timeout(10.seconds)
        .catch { exception ->
          if (exception is TimeoutCancellationException) {
            throw DonationError.BadgeRedemptionError.TimeoutWaitingForTokenError(DonationErrorSource.BACKUPS)
          }
        }
        .first()
    }

    if (terminalInAppPayment.data.error != null) {
      val err = InAppPaymentError(terminalInAppPayment.data.error)
      Log.d(TAG, "An error occurred during the job chain!", err)
      throw err
    } else {
      Log.d(TAG, "Job chain completed successfully.")
      return
    }
  }
}
