/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import android.content.Context
import org.signal.core.util.billing.BillingDependencies
import org.signal.core.util.billing.BillingError
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import java.util.Locale

/**
 * Dependency object for Google Play Billing.
 */
object GooglePlayBillingDependencies : BillingDependencies {

  private const val BILLING_PRODUCT_ID_NOT_AVAILABLE = -1000

  override val context: Context get() = AppDependencies.application

  override suspend fun getProductId(): String {
    val config = AppDependencies.donationsService.getDonationsConfiguration(Locale.getDefault())

    if (config.result.isPresent) {
      return config.result.get().backupConfiguration.backupLevelConfigurationMap[SubscriptionsConfiguration.BACKUPS_LEVEL]?.playProductId ?: throw BillingError(BILLING_PRODUCT_ID_NOT_AVAILABLE)
    } else {
      throw BillingError(BILLING_PRODUCT_ID_NOT_AVAILABLE)
    }
  }

  override suspend fun getBasePlanId(): String {
    return "monthly"
  }
}
