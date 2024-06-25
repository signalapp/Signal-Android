package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.database.InAppPaymentTable

data class GatewaySelectorState(
  val gatewayOrderStrategy: GatewayOrderStrategy,
  val inAppPayment: InAppPaymentTable.InAppPayment,
  val loading: Boolean = true,
  val isGooglePayAvailable: Boolean = false,
  val isPayPalAvailable: Boolean = false,
  val isCreditCardAvailable: Boolean = false,
  val isSEPADebitAvailable: Boolean = false,
  val isIDEALAvailable: Boolean = false,
  val sepaEuroMaximum: FiatMoney? = null
)
