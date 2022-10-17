package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import androidx.core.text.isDigitsOnly

object CreditCardNumberValidator {

  private const val MAX_CARD_NUMBER_LENGTH = 19
  private const val MIN_CARD_NUMBER_LENGTH = 12

  fun getValidity(cardNumber: String, isCardNumberFieldFocused: Boolean): Validity {
    if (cardNumber.length > MAX_CARD_NUMBER_LENGTH || !cardNumber.isDigitsOnly()) {
      return Validity.INVALID
    }

    if (cardNumber.length < MIN_CARD_NUMBER_LENGTH) {
      return Validity.POTENTIALLY_VALID
    }

    val isValid = CreditCardType.fromCardNumber(cardNumber) == CreditCardType.UNIONPAY || isLuhnValid(cardNumber)

    return when {
      isValid -> Validity.FULLY_VALID
      isCardNumberFieldFocused -> Validity.POTENTIALLY_VALID
      else -> Validity.INVALID
    }
  }

  /**
   * An implementation of the [Luhn Algorithm](https://en.wikipedia.org/wiki/Luhn_algorithm) which
   * performs a checksum to check for validity of non-Unionpay cards.
   */
  private fun isLuhnValid(cardNumber: String): Boolean {
    var checksum = 0
    var double = false

    cardNumber.reversed().forEach { char ->
      var digit = char.digitToInt()
      if (double) {
        digit *= 2
      }
      double = !double
      if (digit >= 10) {
        digit -= 9
      }
      checksum += digit
    }

    return checksum % 10 == 0
  }

  enum class Validity {
    INVALID,
    POTENTIALLY_VALID,
    FULLY_VALID
  }
}
