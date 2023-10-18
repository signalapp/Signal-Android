/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.recipients.Recipient

sealed interface GatewayOrderStrategy {

  val orderedGateways: Set<GatewayResponse.Gateway>

  private object Default : GatewayOrderStrategy {
    override val orderedGateways: Set<GatewayResponse.Gateway> = setOf(
      GatewayResponse.Gateway.CREDIT_CARD,
      GatewayResponse.Gateway.PAYPAL,
      GatewayResponse.Gateway.GOOGLE_PAY,
      GatewayResponse.Gateway.SEPA_DEBIT,
      GatewayResponse.Gateway.IDEAL
    )
  }

  private object NorthAmerica : GatewayOrderStrategy {

    override val orderedGateways: Set<GatewayResponse.Gateway> = setOf(
      GatewayResponse.Gateway.GOOGLE_PAY,
      GatewayResponse.Gateway.PAYPAL,
      GatewayResponse.Gateway.CREDIT_CARD,
      GatewayResponse.Gateway.SEPA_DEBIT,
      GatewayResponse.Gateway.IDEAL
    )
  }

  companion object {
    fun getStrategy(): GatewayOrderStrategy {
      val self = Recipient.self()
      val e164 = self.e164.orNull() ?: return Default

      return if (PhoneNumberUtil.getInstance().parse(e164, "").countryCode == 1) {
        NorthAmerica
      } else {
        Default
      }
    }
  }
}
