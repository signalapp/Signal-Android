/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.billing

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Sealed class hierarchy representing the different success
 * and error states of google play billing purchases.
 */
sealed interface BillingPurchaseResult {
  data class Success(
    val purchaseToken: String,
    val isAcknowledged: Boolean,
    val purchaseTime: Long,
    val isAutoRenewing: Boolean
  ) : BillingPurchaseResult {

    /**
     * @return true if purchaseTime is within the last month.
     */
    fun isWithinTheLastMonth(): Boolean {
      val now = System.currentTimeMillis().milliseconds
      val oneMonthAgo = now - 31.days
      val purchaseTime = this.purchaseTime.milliseconds

      return oneMonthAgo >= purchaseTime
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
