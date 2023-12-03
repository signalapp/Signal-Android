/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.signal.donations

import org.json.JSONObject

class IDEALPaymentSource(
  val idealData: StripeApi.IDEALData
) : StripeApi.PaymentSource {
  override val type: PaymentSourceType = PaymentSourceType.Stripe.IDEAL

  override fun parameterize(): JSONObject = error("iDEAL does not support tokenization")

  override fun getTokenId(): String = error("iDEAL does not support tokenization")
  override fun email(): String? = null
}
