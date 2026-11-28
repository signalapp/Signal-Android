/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.billing

import android.content.Context
import org.signal.core.util.billing.BillingApi
import org.signal.core.util.billing.BillingDependencies
import org.signal.core.util.billing.OneTimePurchaseApi

/**
 * Play billing factory. Returns empty implementations when Google Play billing is unavailable.
 */
object BillingFactory {
  @JvmStatic
  fun create(billingDependencies: BillingDependencies, isBackupsAvailable: Boolean): BillingApi {
    return if (isBackupsAvailable) {
      BillingApiImpl(billingDependencies)
    } else {
      BillingApi.Empty
    }
  }

  /**
   * Creates an api for buying consumable products.
   *
   * Google Play only reports a purchase to the client that launched it, so this must not be alive at the same time as
   * another client handling the same product.
   */
  fun createOneTimePurchaseApi(context: Context, isAvailable: Boolean): OneTimePurchaseApi {
    return if (isAvailable) {
      OneTimePurchaseApiImpl(context)
    } else {
      OneTimePurchaseApi.Empty
    }
  }
}
