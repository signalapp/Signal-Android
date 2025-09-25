/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.migrations

import kotlinx.coroutines.runBlocking
import okio.IOException
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.whispersystems.signalservice.api.storage.IAPSubscriptionId

/**
 * When we migrate subscriptions, purchase tokens are stored as '-' string. This migration
 * goes in and updates that purchase token with the real value from the latest subscription, if
 * available.
 */
internal class GooglePlayBillingPurchaseTokenMigrationJob private constructor(
  parameters: Parameters
) : MigrationJob(parameters) {

  constructor() : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .build()
  )

  companion object {
    private val TAG = Log.tag(GooglePlayBillingPurchaseTokenMigrationJob::class)

    const val KEY = "GooglePlayBillingPurchaseTokenMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (!SignalStore.account.isRegistered) {
      return
    }

    val backupSubscriber = InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP) ?: return

    if (backupSubscriber.iapSubscriptionId?.purchaseToken == "-") {
      val purchaseResult: BillingPurchaseResult.Success? = runBlocking {
        if (AppDependencies.billingApi.getApiAvailability().isSuccess) {
          val purchase = AppDependencies.billingApi.queryPurchases()

          if (purchase is BillingPurchaseResult.Success) {
            Log.d(TAG, "Successfully found purchase result.")
            purchase
          } else {
            Log.d(TAG, "No purchase was available.")
            null
          }
        } else {
          Log.d(TAG, "Billing API is not available.")
          null
        }
      }

      if (purchaseResult == null) {
        return
      }

      InAppPaymentsRepository.setSubscriber(
        backupSubscriber.copy(
          iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken(purchaseToken = purchaseResult.purchaseToken)
        )
      )

      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  override fun shouldRetry(e: Exception): Boolean {
    Log.w(TAG, "Checking retry state for exception.", e)
    return e is IOException
  }

  class Factory : Job.Factory<GooglePlayBillingPurchaseTokenMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): GooglePlayBillingPurchaseTokenMigrationJob {
      return GooglePlayBillingPurchaseTokenMigrationJob(parameters)
    }
  }
}
