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
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.backups.BackupStateObserver
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
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.storage.IAPSubscriptionId
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.seconds

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
      Log.i(TAG, "User is not registered. Clearing mismatch value and exiting.", true)
      SignalStore.backup.subscriptionStateMismatchDetected = false
      return Result.success()
    }

    if (SignalStore.account.isLinkedDevice) {
      Log.i(TAG, "Linked device. Clearing mismatch value and exiting.", true)
      SignalStore.backup.subscriptionStateMismatchDetected = false
      return Result.success()
    }

    if (!RemoteConfig.messageBackups) {
      Log.i(TAG, "Message backups feature is not available. Clearing mismatch value and exiting.", true)
      SignalStore.backup.subscriptionStateMismatchDetected = false
      return Result.success()
    }

    if (!AppDependencies.billingApi.isApiAvailable()) {
      Log.i(TAG, "Google Play Billing API is not available on this device. Clearing mismatch value and exiting.", true)
      SignalStore.backup.subscriptionStateMismatchDetected = false
      return Result.success()
    }

    if (SignalStore.backup.deletionState != DeletionState.NONE) {
      Log.i(TAG, "User is in the process of or has delete their backup. Clearing mismatch value and exiting.", true)
      SignalStore.backup.subscriptionStateMismatchDetected = false
      return Result.success()
    }

    if (!SignalStore.backup.areBackupsEnabled) {
      Log.i(TAG, "Backups are not enabled on this device. Clearing mismatch value and exiting.", true)
      SignalStore.backup.subscriptionStateMismatchDetected = false
      return Result.success()
    }

    if (SignalStore.backup.backupTierInternalOverride != null) {
      Log.i(TAG, "User has internal override set for backup version. Clearing mismatch value and exiting.", true)
      SignalStore.backup.subscriptionStateMismatchDetected = false
      return Result.success()
    }

    val purchase: BillingPurchaseResult = AppDependencies.billingApi.queryPurchases()
    Log.i(TAG, "Retrieved purchase result from Billing api: $purchase", true)

    val hasActivePurchase = purchase is BillingPurchaseResult.Success && purchase.isAcknowledged
    val product: BillingProduct? = AppDependencies.billingApi.queryProduct()

    if (product == null) {
      Log.w(TAG, "Google Play Billing product not available. Exiting.", true)
      return Result.failure()
    }

    InAppPaymentSubscriberRecord.Type.BACKUP.lock.withLock {
      val inAppPayment = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_BACKUP)

      if (inAppPayment?.state == InAppPaymentTable.State.PENDING) {
        Log.i(TAG, "User has a pending in-app payment. Clearing mismatch value and re-checking later.", true)
        SignalStore.backup.subscriptionStateMismatchDetected = false
        return Result.success()
      }

      val activeSubscription = RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP).getOrNull()
      val hasActiveSignalSubscription = activeSubscription?.isActive == true

      checkForFailedOrCanceledSubscriptionState(activeSubscription)

      val isSignalSubscriptionFailedOrCanceled = activeSubscription?.willCancelAtPeriodEnd() == true
      if (hasActiveSignalSubscription && !isSignalSubscriptionFailedOrCanceled) {
        checkAndSynchronizeZkCredentialTierWithStoredLocalTier()
      }

      val hasActivePaidBackupTier = SignalStore.backup.backupTier == MessageBackupTier.PAID
      val hasValidActiveState = hasActivePaidBackupTier && hasActiveSignalSubscription && hasActivePurchase
      val hasValidInactiveState = !hasActivePaidBackupTier && !hasActiveSignalSubscription && !hasActivePurchase

      val purchaseToken = if (hasActivePurchase) {
        purchase.purchaseToken
      } else {
        null
      }

      val hasTokenMismatch = purchaseToken?.let { hasLocalDevicePurchaseTokenMismatch(purchaseToken) } == true
      if (hasActiveSignalSubscription && hasTokenMismatch) {
        Log.i(TAG, "Encountered token mismatch with an active Signal subscription. Attempting to redeem against latest token.", true)
        rotateAndRedeem(purchaseToken, product.price)
        SignalStore.backup.subscriptionStateMismatchDetected = false
        return Result.success()
      } else if (purchaseToken != null && hasActiveSignalSubscription && !hasActivePaidBackupTier && !SignalDatabase.inAppPayments.hasPendingBackupRedemption()) {
        Log.i(TAG, "We have an active signal subscription and active purchase, but no entitlement and no pending redemption. Enqueuing a redemption now.")
        rotateAndRedeem(purchaseToken, product.price)
        SignalStore.backup.subscriptionStateMismatchDetected = false
        return Result.success()
      } else {
        if (hasValidActiveState || hasValidInactiveState) {
          Log.i(TAG, "Valid state: (hasValidActiveState: $hasValidActiveState, hasValidInactiveState: $hasValidInactiveState). Clearing mismatch value and exiting.", true)
          SignalStore.backup.subscriptionStateMismatchDetected = false
          return Result.success()
        } else {
          val isGooglePlayBillingCanceled = purchase is BillingPurchaseResult.Success && !purchase.isAutoRenewing

          if (isGooglePlayBillingCanceled && (!hasActiveSignalSubscription || isSignalSubscriptionFailedOrCanceled)) {
            Log.i(
              TAG,
              "Valid cancel state. Clearing mismatch. (isGooglePlayBillingCanceled: true, hasActiveSignalSubscription: $hasActiveSignalSubscription, isSignalSubscriptionFailedOrCanceled: $isSignalSubscriptionFailedOrCanceled",
              true
            )
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
  }

  /**
   * If we detect that we have an active subscription, we want to check to make sure our ZK credentials are good. If they aren't, we should clear them.
   * This will also synchronize our backup tier value with whatever the refreshed Zk tier thinks we are on, if necessary.
   */
  private fun checkAndSynchronizeZkCredentialTierWithStoredLocalTier() {
    Log.i(TAG, "Detected an active, non-failed, non-canceled signal subscription. Synchronizing backup tier with value from server.", true)

    val zkTier: MessageBackupTier? = when (val result = BackupRepository.getBackupTierWithoutDowngrade()) {
      is NetworkResult.Success -> result.result
      else -> null
    }

    if (zkTier == SignalStore.backup.backupTier) {
      Log.i(TAG, "ZK credential tier is in sync with our stored backup tier.", true)
    } else {
      Log.w(TAG, "ZK credential tier is not in sync with our stored backup tier, flushing credentials and retrying.", true)
      BackupRepository.resetInitializedStateAndAuthCredentials()

      BackupRepository.getBackupTier().runIfSuccessful {
        Log.i(TAG, "Refreshed credentials. Synchronizing stored backup tier with ZK result.")
        SignalStore.backup.backupTier = it
      }
    }
  }

  /**
   * Checks for a payment failure / subscription cancellation. If either of these things occur, we will mark when to display
   * the "download your data" notifier sheet.
   */
  private fun checkForFailedOrCanceledSubscriptionState(activeSubscription: ActiveSubscription?) {
    if (activeSubscription?.willCancelAtPeriodEnd() == true && activeSubscription?.activeSubscription != null) {
      Log.i(TAG, "Subscription either has a payment failure or has been canceled.")

      val response = SignalNetwork.account.whoAmI()
      response.runIfSuccessful { whoAmI ->
        val backupExpiration = whoAmI.entitlements?.backup?.expirationSeconds?.seconds
        if (backupExpiration != null) {
          Log.i(TAG, "Marking subscription failed or canceled.")
          SignalStore.backup.setDownloadNotifierToTriggerAtHalfwayPoint(backupExpiration)
          BackupStateObserver.notifyBackupStateChanged()
        } else {
          Log.w(TAG, "Failed to mark, no entitlement was found on WhoAmIResponse")
        }
      }

      if (response.getCause() != null) {
        Log.w(TAG, "Failed to get WhoAmI from service.", response.getCause())
      }
    }
  }

  private fun rotateAndRedeem(localDevicePurchaseToken: String, localProductPrice: FiatMoney) {
    RecurringInAppPaymentRepository.ensureSubscriberIdSync(
      subscriberType = InAppPaymentSubscriberRecord.Type.BACKUP,
      isRotation = true,
      iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken(localDevicePurchaseToken)
    )

    SignalDatabase.inAppPayments.clearCreated()

    val id = SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_BACKUP,
      state = InAppPaymentTable.State.PENDING,
      subscriberId = InAppPaymentsRepository.requireSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP).subscriberId,
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

    InAppPaymentPurchaseTokenJob.createJobChain(
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
