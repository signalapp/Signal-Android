/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.signal.donations

class IDEALPaymentSource(
  val idealData: StripeApi.IDEALData
) : PaymentSource {
  override val type: PaymentSourceType = PaymentSourceType.Stripe.IDEAL

  override fun email(): String? = null
}
