/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.donations

/**
 * Request JSON for creating a Stripe one-time payment intent
 */
internal class StripeOneTimePaymentIntentPayload(
  val amount: Long,
  val currency: String,
  val level: Long,
  val paymentMethod: String
)
