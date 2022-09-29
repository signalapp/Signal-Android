package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GatewayResponse(val gateway: Gateway, val request: GatewayRequest) : Parcelable {
  enum class Gateway {
    GOOGLE_PAY,
    PAYPAL,
    CREDIT_CARD
  }
}
