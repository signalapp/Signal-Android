package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalType
import java.math.BigDecimal
import java.util.Currency

@Parcelize
data class GatewayRequest(
  val donateToSignalType: DonateToSignalType,
  val badge: Badge,
  val label: String,
  val price: BigDecimal,
  val currencyCode: String,
  val level: Long
) : Parcelable {
  val fiat: FiatMoney = FiatMoney(price, Currency.getInstance(currencyCode))
}
