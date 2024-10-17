/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.billing

import org.signal.core.util.money.FiatMoney

/**
 * Represents a purchasable product from the Google Play Billing API
 */
data class BillingProduct(
  val price: FiatMoney
)
