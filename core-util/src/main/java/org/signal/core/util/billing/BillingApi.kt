/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.billing

import android.app.Activity

/**
 * Variant interface for the BillingApi.
 */
interface BillingApi {
  fun isApiAvailable(): Boolean = false
  suspend fun queryProducts() = Unit

  /**
   * Queries the user's current purchases. This enqueues a check and will
   * propagate it to the normal callbacks in the api.
   */
  suspend fun queryPurchases() = Unit

  suspend fun launchBillingFlow(activity: Activity) = Unit

  /**
   * Empty implementation, to be used when play services are available but
   * GooglePlayBillingApi is not available.
   */
  object Empty : BillingApi
}
