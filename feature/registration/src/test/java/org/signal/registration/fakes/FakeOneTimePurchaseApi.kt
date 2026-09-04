/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.fakes

import org.signal.core.util.billing.BillingPurchaseState
import org.signal.core.util.billing.OneTimeProduct
import org.signal.core.util.billing.OneTimeProductId
import org.signal.core.util.billing.OneTimeProductResult
import org.signal.core.util.billing.OneTimePurchase
import org.signal.core.util.billing.OneTimePurchaseApi
import org.signal.core.util.billing.OneTimePurchasePreparation
import org.signal.core.util.billing.OneTimePurchaseResult
import org.signal.core.util.billing.PurchaseLauncher

/**
 * Stands in for Google Play. Holds an optional owned purchase, which [preparePurchase] reports as already owned
 * rather than "charging", mirroring the real implementation's handling of an unconsumed purchase.
 */
class FakeOneTimePurchaseApi(
  var formattedPrice: String? = "$1.99",
  var ownedPurchase: OneTimePurchase? = null
) : OneTimePurchaseApi {

  var onLaunchPurchaseFlow: (OneTimeProductId) -> OneTimePurchaseResult = { OneTimePurchaseResult.Success(purchase()) }

  var closeCount: Int = 0

  val requestedProducts = mutableListOf<OneTimeProductId>()
  val consumedTokens = mutableListOf<String>()
  var consumeSucceeds: Boolean = true

  /** What [queryProduct] reports when [formattedPrice] is null -- lets a test pick a retryable failure over a terminal one. */
  var productResultWhenUnpriced: OneTimeProductResult = OneTimeProductResult.Unavailable
  var launchCount: Int = 0

  override suspend fun queryProduct(product: OneTimeProductId): OneTimeProductResult {
    requestedProducts += product
    return formattedPrice?.let { OneTimeProductResult.Success(OneTimeProduct(formattedPrice = it)) } ?: productResultWhenUnpriced
  }

  override suspend fun queryUnconsumedPurchase(product: OneTimeProductId): OneTimePurchase? {
    requestedProducts += product
    return ownedPurchase
  }

  override suspend fun preparePurchase(product: OneTimeProductId): OneTimePurchasePreparation {
    requestedProducts += product
    ownedPurchase?.let { return OneTimePurchasePreparation.AlreadyOwned(it) }
    return OneTimePurchasePreparation.Ready(
      PurchaseLauncher {
        launchCount++
        onLaunchPurchaseFlow(product)
      }
    )
  }

  override fun close() {
    closeCount++
  }

  override suspend fun consumePurchase(purchaseToken: String): Boolean {
    consumedTokens += purchaseToken
    if (consumeSucceeds) {
      ownedPurchase = null
    }
    return consumeSucceeds
  }

  companion object {
    const val PURCHASE_TOKEN = "fake-purchase-token"

    fun purchase(
      purchaseToken: String = PURCHASE_TOKEN,
      state: BillingPurchaseState = BillingPurchaseState.PURCHASED
    ): OneTimePurchase = OneTimePurchase(purchaseToken = purchaseToken, state = state)
  }
}
