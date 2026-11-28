/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.billing

/**
 * Pricing for a [OneTimeProductId], as reported by Google Play.
 *
 * @param formattedPrice The price, already formatted in the currency and locale of the user's Play account.
 */
data class OneTimeProduct(
  val formattedPrice: String
)
