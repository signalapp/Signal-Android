/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.donations

class PayPalPaymentSource : PaymentSource {
  override val type: PaymentSourceType = PaymentSourceType.PayPal
}
