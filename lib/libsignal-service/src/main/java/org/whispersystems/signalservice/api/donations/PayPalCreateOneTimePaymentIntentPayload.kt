/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.donations

/**
 * Request JSON for creating a PayPal one-time payment intent
 */
internal class PayPalCreateOneTimePaymentIntentPayload(
  val amount: Long,
  val currency: String,
  val level: Long,
  val returnUrl: String,
  val cancelUrl: String
)
