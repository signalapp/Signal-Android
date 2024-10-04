/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Variant interface for the BillingApi.
 */
interface BillingApi {
  /**
   * Listenable stream of billing purchase results. It's up to the user
   * to call queryPurchases after subscription.
   */
  fun getBillingPurchaseResults(): Flow<BillingPurchaseResult> = emptyFlow()

  fun isApiAvailable(): Boolean = false

  suspend fun queryProduct(): BillingProduct? = null

  /**
   * Queries the user's current purchases. This enqueues a check and will
   * propagate it to the normal callbacks in the api.
   */
  suspend fun queryPurchases(): BillingPurchaseResult = BillingPurchaseResult.None

  suspend fun launchBillingFlow(activity: Activity) = Unit

  /**
   * Empty implementation, to be used when play services are available but
   * GooglePlayBillingApi is not available.
   */
  object Empty : BillingApi
}
