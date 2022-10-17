package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import androidx.core.text.isDigitsOnly

object CreditCardCodeValidator {

  fun getValidity(code: String, cardType: CreditCardType, isFocused: Boolean): Validity {
    val validLength = if (cardType == CreditCardType.AMERICAN_EXPRESS) {
      4
    } else {
      3
    }

    return when {
      !code.isDigitsOnly() -> Validity.INVALID_CHARACTERS
      code.length > validLength -> Validity.TOO_LONG
      code.length < validLength && isFocused -> Validity.POTENTIALLY_VALID
      code.isEmpty() -> Validity.POTENTIALLY_VALID
      code.length < validLength -> Validity.TOO_SHORT
      else -> Validity.FULLY_VALID
    }
  }

  enum class Validity {
    TOO_LONG,
    TOO_SHORT,
    INVALID_CHARACTERS,
    POTENTIALLY_VALID,
    FULLY_VALID
  }
}
