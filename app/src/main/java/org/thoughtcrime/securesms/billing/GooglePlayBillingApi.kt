/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.billing

/**
 * Variant interface for the BillingApi.
 */
interface GooglePlayBillingApi {
  fun isApiAvailable(): Boolean = false
  suspend fun queryProducts() {}

  /**
   * Empty implementation, to be used when play services are available but
   * GooglePlayBillingApi is not available.
   */
  object Empty : GooglePlayBillingApi
}
