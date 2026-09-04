/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.billing

/**
 * Interface for non-subscription Google Play Purchases. This is for items that users can buy repeatedly.
 */
interface OneTimePurchaseApi {

  /**
   * Localized pricing for [product].
   */
  suspend fun queryProduct(product: OneTimeProductId): OneTimeProductResult = OneTimeProductResult.Unavailable

  /**
   * The most recent purchase of [product] that has not been consumed yet, if any. A purchase stays queryable until
   * [consumePurchase] succeeds.
   */
  suspend fun queryUnconsumedPurchase(product: OneTimeProductId): OneTimePurchase? = null

  /**
   * Does everything a purchase of [product] needs short of showing the sheet: resolves the offer, and reuses an
   * unconsumed purchase if the user already owns one rather than setting up to charge them twice.
   */
  suspend fun preparePurchase(product: OneTimeProductId): OneTimePurchasePreparation = OneTimePurchasePreparation.Unavailable

  /**
   * Consumes a purchase, making the product purchasable again. Must only be called once the purchase has been redeemed
   * for whatever it buys, because a consumed token can no longer be verified with Google Play.
   *
   * @return whether Google Play accepted the consumption.
   */
  suspend fun consumePurchase(purchaseToken: String): Boolean = false

  /**
   * Releases the billing connection and the thread behind it.
   */
  fun close() = Unit

  /**
   * Empty implementation, for builds where Google Play billing is unavailable.
   */
  object Empty : OneTimePurchaseApi
}
