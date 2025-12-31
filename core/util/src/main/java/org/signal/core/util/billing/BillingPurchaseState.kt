/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.billing

/**
 * BillingPurchaseState which aligns with the Google Play Billing purchased state.
 */
enum class BillingPurchaseState {
  UNSPECIFIED,
  PURCHASED,
  PENDING
}
