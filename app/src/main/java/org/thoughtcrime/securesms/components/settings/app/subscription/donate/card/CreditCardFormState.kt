package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import org.signal.donations.StripeApi

data class CreditCardFormState(
  val focusedField: FocusedField = FocusedField.NONE,
  val number: String = "",
  val expiration: CreditCardExpiration = CreditCardExpiration(),
  val code: String = ""
) {
  enum class FocusedField {
    NONE,
    NUMBER,
    EXPIRATION,
    CODE
  }

  fun toCardData(): StripeApi.CardData {
    return StripeApi.CardData(
      number,
      expiration.month.toInt(),
      expiration.year.toInt(),
      code.toInt()
    )
  }
}
