/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.donations

/**
 * Request JSON for creating a recurring PayPal payment method
 */
internal class PayPalCreatePaymentMethodPayload(
  val returnUrl: String,
  val cancelUrl: String
)
