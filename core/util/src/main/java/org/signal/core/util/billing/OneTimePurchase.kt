/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.billing

import org.signal.core.util.censor

/**
 * A completed or in-flight one-time purchase.
 *
 * @param purchaseToken The token Google Play issued, which a server can verify the purchase with.
 * @param state Whether the purchase has settled. A [BillingPurchaseState.PENDING] purchase may still settle later.
 */
data class OneTimePurchase(
  val purchaseToken: String,
  val state: BillingPurchaseState
) {
  override fun toString(): String = "OneTimePurchase(purchaseToken=${purchaseToken.censor()}, state=$state)"
}
