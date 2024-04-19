/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppDonations
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.recipients.Recipient

sealed interface GatewayOrderStrategy {

  val orderedGateways: Set<InAppPaymentData.PaymentMethodType>

  private object Default : GatewayOrderStrategy {
    override val orderedGateways: Set<InAppPaymentData.PaymentMethodType> = setOf(
      InAppPaymentData.PaymentMethodType.CARD,
      InAppPaymentData.PaymentMethodType.PAYPAL,
      InAppPaymentData.PaymentMethodType.GOOGLE_PAY,
      InAppPaymentData.PaymentMethodType.SEPA_DEBIT,
      InAppPaymentData.PaymentMethodType.IDEAL
    )
  }

  private object NorthAmerica : GatewayOrderStrategy {
    override val orderedGateways: Set<InAppPaymentData.PaymentMethodType> = setOf(
      InAppPaymentData.PaymentMethodType.GOOGLE_PAY,
      InAppPaymentData.PaymentMethodType.PAYPAL,
      InAppPaymentData.PaymentMethodType.CARD,
      InAppPaymentData.PaymentMethodType.SEPA_DEBIT,
      InAppPaymentData.PaymentMethodType.IDEAL
    )
  }

  private object Netherlands : GatewayOrderStrategy {
    override val orderedGateways: Set<InAppPaymentData.PaymentMethodType> = setOf(
      InAppPaymentData.PaymentMethodType.IDEAL,
      InAppPaymentData.PaymentMethodType.PAYPAL,
      InAppPaymentData.PaymentMethodType.GOOGLE_PAY,
      InAppPaymentData.PaymentMethodType.CARD,
      InAppPaymentData.PaymentMethodType.SEPA_DEBIT
    )
  }

  companion object {
    fun getStrategy(): GatewayOrderStrategy {
      val self = Recipient.self()
      val e164 = self.e164.orNull() ?: return Default

      return if (PhoneNumberUtil.getInstance().parse(e164, "").countryCode == 1) {
        NorthAmerica
      } else if (InAppDonations.isIDEALAvailable()) {
        Netherlands
      } else {
        Default
      }
    }
  }
}
