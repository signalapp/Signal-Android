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
    val purchaseState: BillingPurchaseState,
    val purchaseToken: String,
    val isAcknowledged: Boolean,
    val purchaseTime: Long,
    val isAutoRenewing: Boolean
  ) : BillingPurchaseResult {

    override fun toString(): String {
      return """
        BillingPurchaseResult {
          purchaseState: $purchaseState
          purchaseToken: <redacted>
          purchaseTime: $purchaseTime
          isAcknowledged: $isAcknowledged
          isAutoRenewing: $isAutoRenewing
        }
      """.trimIndent()
    }
  }
  data object UserCancelled : BillingPurchaseResult
  data object None : BillingPurchaseResult
  data object TryAgainLater : BillingPurchaseResult
  data object AlreadySubscribed : BillingPurchaseResult
  data object FeatureNotSupported : BillingPurchaseResult
  data object GenericError : BillingPurchaseResult
  data object NetworkError : BillingPurchaseResult
  data object BillingUnavailable : BillingPurchaseResult
}
