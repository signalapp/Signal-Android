/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import androidx.annotation.VisibleForTesting
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig

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
      Log.i(TAG, "User is not registered. Exiting.")
      return Result.success()
    }

    if (!RemoteConfig.messageBackups) {
      Log.i(TAG, "Message backups are not enabled. Exiting.")
      return Result.success()
    }

    if (!SignalStore.backup.areBackupsEnabled) {
      Log.i(TAG, "Backups are not enabled on this device. Exiting.")
      return Result.success()
    }

    if (!AppDependencies.billingApi.isApiAvailable()) {
      Log.i(TAG, "Google Play Billing API is not available on this device. Exiting.")
      return Result.success()
    }

    val purchase: BillingPurchaseResult = AppDependencies.billingApi.queryPurchases()
    val hasActivePurchase = purchase is BillingPurchaseResult.Success && purchase.isAcknowledged && purchase.isWithinTheLastMonth()

    val subscriberId = InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP)
    if (subscriberId == null && hasActivePurchase) {
      Log.w(TAG, "User has active Google Play Billing purchase but no subscriber id! User should cancel backup and resubscribe.")
      // TODO [message-backups] Set UI flag hint here to launch sheet (designs pending)
      return Result.success()
    }

    val tier = SignalStore.backup.backupTier
    if (subscriberId == null && tier == MessageBackupTier.PAID) {
      Log.w(TAG, "User has no subscriber id but PAID backup tier. User will need to cancel and resubscribe.")
      // TODO [message-backups] Set UI flag hint here to launch sheet (designs pending)
      return Result.success()
    }

    val activeSubscription = RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP).getOrNull()
    if (activeSubscription?.isActive == true && tier != MessageBackupTier.PAID) {
      Log.w(TAG, "User has an active subscription but no backup tier.")
      // TODO [message-backups] Set UI flag hint here to launch error sheet?
      return Result.success()
    }

    if (activeSubscription?.isActive != true && tier == MessageBackupTier.PAID) {
      Log.w(TAG, "User subscription is inactive or does not exist. User will need to cancel and resubscribe.")
      // TODO [message-backups] Set UI hint?
      return Result.success()
    }

    if (activeSubscription?.isActive != true && hasActivePurchase) {
      Log.w(TAG, "User subscription is inactive but user has a recent purchase. User will need to cancel and resubscribe.")
      // TODO [message-backups] Set UI hint?
      return Result.success()
    }

    return Result.success()
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
