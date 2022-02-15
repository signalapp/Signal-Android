package org.thoughtcrime.securesms.components.settings.app.subscription.boost

import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.badges.models.Badge
import java.math.BigDecimal
import java.util.Currency

data class BoostState(
  val boostBadge: Badge? = null,
  val currencySelection: Currency,
  val isGooglePayAvailable: Boolean = false,
  val boosts: List<Boost> = listOf(),
  val selectedBoost: Boost? = null,
  val customAmount: FiatMoney = FiatMoney(BigDecimal.ZERO, currencySelection),
  val isCustomAmountFocused: Boolean = false,
  val stage: Stage = Stage.INIT,
  val supportedCurrencyCodes: List<String> = emptyList()
) {
  enum class Stage {
    INIT,
    READY,
    TOKEN_REQUEST,
    PAYMENT_PIPELINE,
    FAILURE
  }
}
