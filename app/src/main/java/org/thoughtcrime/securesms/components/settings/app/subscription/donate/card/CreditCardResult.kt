package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayRequest

/**
 * Encapsulates data returned from the credit card form that can be used
 * for a credit card based donation payment.
 */
@Parcelize
data class CreditCardResult(
  val gatewayRequest: GatewayRequest,
  val creditCardData: StripeApi.CardData
) : Parcelable
