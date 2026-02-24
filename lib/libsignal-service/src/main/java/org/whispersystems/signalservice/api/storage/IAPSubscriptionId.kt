/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.storage

import org.signal.core.util.isNotNullOrBlank
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Represents an In-app purchase subscription id, whose form depends on what platform the subscription originated on.
 */
sealed class IAPSubscriptionId(open val purchaseToken: String?, open val originalTransactionId: Long?) {
  /**
   * Represents a Google Play Billing subscription, identified by the purchase token.
   */
  data class GooglePlayBillingPurchaseToken(override val purchaseToken: String) : IAPSubscriptionId(purchaseToken, null)

  /**
   * Represents an Apple IAP subscription, identified by the original transaction id.
   */
  data class AppleIAPOriginalTransactionId(override val originalTransactionId: Long) : IAPSubscriptionId(null, originalTransactionId)

  companion object {
    /**
     * Checks the given proto for valid IAP subscription data and creates an ID for it.
     * If there is no valid data, we return null.
     */
    fun from(proto: AccountRecord.IAPSubscriberData?): IAPSubscriptionId? {
      return if (proto == null) {
        null
      } else if (proto.purchaseToken.isNotNullOrBlank()) {
        GooglePlayBillingPurchaseToken(proto.purchaseToken)
      } else if (proto.originalTransactionId != null) {
        AppleIAPOriginalTransactionId(proto.originalTransactionId)
      } else {
        null
      }
    }

    @OptIn(ExperimentalContracts::class)
    fun IAPSubscriptionId?.isNotNullOrBlank(): Boolean {
      contract {
        returns(true) implies (this@isNotNullOrBlank != null)
      }

      return this != null && (purchaseToken.isNotNullOrBlank() || originalTransactionId != null)
    }
  }
}
