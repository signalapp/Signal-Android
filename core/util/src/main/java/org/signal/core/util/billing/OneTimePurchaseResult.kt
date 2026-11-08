/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.billing

/**
 * Outcome of the purchase sheet launched by [PurchaseLauncher.launch].
 */
sealed interface OneTimePurchaseResult {
  data class Success(val purchase: OneTimePurchase) : OneTimePurchaseResult

  data object UserCancelled : OneTimePurchaseResult

  /** Google Play billing isn't usable here, or the product isn't purchasable. */
  data object Unavailable : OneTimePurchaseResult

  data object NetworkError : OneTimePurchaseResult

  data object GenericError : OneTimePurchaseResult
}
