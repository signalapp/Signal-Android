/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.billing

/**
 * Identifies a purchasable one-time product in the Play Console.
 *
 * @param productId The product's id.
 * @param purchaseOptionId The id of the purchase option to buy. A one-time product may offer several, and each carries
 *   its own price and offer token.
 */
data class OneTimeProductId(
  val productId: String,
  val purchaseOptionId: String
)
