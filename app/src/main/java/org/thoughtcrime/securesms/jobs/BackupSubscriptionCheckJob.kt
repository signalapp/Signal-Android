/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import androidx.annotation.VisibleForTesting
import org.signal.core.util.billing.BillingProduct
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatValue
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.storage.IAPSubscriptionId
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import kotlin.concurrent.withLock

/**
 * Checks and rectifies state pertaining to backups subscriptions.
 */
class BackupSubscriptionCheckJob private constructor(parameters: Parameters) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(BackupSubscriptionCheckJob::class)

    const val KEY = "BackupSubscriptionCheckJob"

    @VisibleForTesting
    fun create(): BackupSubscriptionCheckJob {
      return BackupSubscriptionCheckJob(
        Parameters.Builder()
          .setQueue(InAppPaymentsRepository.getRecurringJobQueueKey(InAppPaymentType.RECURRING_BACKUP))
          .addConstraint(NetworkConstraint.KEY)
          .setMaxAttempts(Parameters.UNLIMITED)
          .setMaxInstancesForFactory(1)
          .build()
      )
    }

    @JvmStatic
    fun enqueueIfAble() {
      if (!RemoteConfig.messageBackups) {
        return
      }

      val job = create()

      AppDependencies.jobManager.add(job)
    }
  }

  override suspend fun doRun(): Result {
    if (!SignalStore.account.isRegistered) {
      Log.i(TAG, "User is not registered. Clearing mismatch value and exiting.")
      SignalStore.backup.subscriptionStateMismatchDetected = false
      return Result.success()
    }

    if (!RemoteConfig.messageBackups) {
      Log.i(TAG, "Message backups are not enabled. Clearing mismatch value and exiting.")
      SignalStore.backup.subscriptionStateMismatchDetected = false
      return Result.success()
    }

    if (!AppDependencies.billingApi.isApiAvailable()) {
      Log.i(TAG, "Google Play Billing API is not available on this device. Clearing mismatch value and exiting.")
      SignalStore.backup.subscriptionStateMismatchDetected = false
      return Result.success()
    }

    val purchase: BillingPurchaseResult = AppDependencies.billingApi.queryPurchases()
    Log.i(TAG, "Retrieved purchase result from Billing api: $purchase")

    val hasActivePurchase = purchase is BillingPurchaseResult.Success && purchase.isAcknowledged && purchase.isWithinTheLastMonth()
    val product: BillingProduct? = AppDependencies.billingApi.queryProduct()

    if (product == null) {
      Log.w(TAG, "Google Play Billing product not available. Exiting.")
      return Result.failure()
    }

    InAppPaymentSubscriberRecord.Type.BACKUP.lock.withLock {
      val inAppPayment = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_BACKUP)

      if (inAppPayment?.state == InAppPaymentTable.State.PENDING) {
        Log.i(TAG, "User has a pending in-app payment. Clearing mismatch value and re-checking later.")
        SignalStore.backup.subscriptionStateMismatchDetected = false
        return Result.success()
      }

      val activeSubscription = RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP).getOrNull()
      val hasActiveSignalSubscription = activeSubscription?.isActive == true

      Log.i(TAG, "Synchronizing backup tier with value from server.")
      BackupRepository.getBackupTier().runIfSuccessful {
        SignalStore.backup.backupTier = it
      }

      val hasActivePaidBackupTier = SignalStore.backup.backupTier == MessageBackupTier.PAID
      val hasValidActiveState = hasActivePaidBackupTier && hasActiveSignalSubscription && hasActivePurchase
      val hasValidInactiveState = !hasActivePaidBackupTier && !hasActiveSignalSubscription && !hasActivePurchase

      val purchaseToken = if (hasActivePurchase) {
        (purchase as BillingPurchaseResult.Success).purchaseToken
      } else {
        null
      }

      if (purchaseToken?.let { hasLocalDevicePurchaseTokenMismatch(purchaseToken) } == true) {
        Log.i(TAG, "Encountered token mismatch. Attempting to redeem.")
        enqueueRedemptionForNewToken(purchaseToken, product.price)
        SignalStore.backup.subscriptionStateMismatchDetected = false
        return Result.success()
      } else {
        if (hasValidActiveState || hasValidInactiveState) {
          Log.i(TAG, "Valid state: (hasValidActiveState: $hasValidActiveState, hasValidInactiveState: $hasValidInactiveState). Clearing mismatch value and exiting.", true)
          SignalStore.backup.subscriptionStateMismatchDetected = false
          return Result.success()
        } else {
          Log.w(TAG, "State mismatch: (hasActivePaidBackupTier: $hasActivePaidBackupTier, hasActiveSignalSubscription: $hasActiveSignalSubscription, hasActivePurchase: $hasActivePurchase). Setting mismatch value and exiting.", true)
          SignalStore.backup.subscriptionStateMismatchDetected = true
          return Result.success()
        }
      }
    }
  }

  private fun enqueueRedemptionForNewToken(localDevicePurchaseToken: String, localProductPrice: FiatMoney) {
    RecurringInAppPaymentRepository.ensureSubscriberId(
      subscriberType = InAppPaymentSubscriberRecord.Type.BACKUP,
      isRotation = true,
      iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken(localDevicePurchaseToken)
    ).blockingAwait()

    SignalDatabase.inAppPayments.clearCreated()

    val id = SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_BACKUP,
      state = InAppPaymentTable.State.CREATED,
      subscriberId = null,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData(
        badge = null,
        amount = localProductPrice.toFiatValue(),
        level = SubscriptionsConfiguration.BACKUPS_LEVEL.toLong(),
        recipientId = Recipient.self().id.serialize(),
        paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
        redemption = InAppPaymentData.RedemptionState(
          stage = InAppPaymentData.RedemptionState.Stage.INIT
        )
      )
    )

    InAppPaymentRecurringContextJob.createJobChain(
      inAppPayment = SignalDatabase.inAppPayments.getById(id)!!
    ).enqueue()
  }

  private fun hasLocalDevicePurchaseTokenMismatch(localDevicePurchaseToken: String): Boolean {
    val subscriber = InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP)

    return subscriber?.iapSubscriptionId?.purchaseToken != localDevicePurchaseToken
  }

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  class Factory : Job.Factory<BackupSubscriptionCheckJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackupSubscriptionCheckJob {
      return BackupSubscriptionCheckJob(parameters)
    }
  }
}
