/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.billing

/**
 * Sealed class hierarchy representing the different success
 * and error states of google play billing purchases.
 */
sealed interface BillingPurchaseResult {
  data class Success(
    val purchaseToken: String,
    val isAcknowledged: Boolean,
    val purchaseTime: Long
  ) : BillingPurchaseResult
  data object UserCancelled : BillingPurchaseResult
  data object None : BillingPurchaseResult
  data object TryAgainLater : BillingPurchaseResult
  data object AlreadySubscribed : BillingPurchaseResult
  data object FeatureNotSupported : BillingPurchaseResult
  data object GenericError : BillingPurchaseResult
  data object NetworkError : BillingPurchaseResult
  data object BillingUnavailable : BillingPurchaseResult
}
