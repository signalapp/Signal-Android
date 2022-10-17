package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import androidx.core.text.isDigitsOnly

object CreditCardExpirationValidator {

  fun getValidity(creditCardExpiration: CreditCardExpiration, currentMonth: Int, currentYear: Int, isFocused: Boolean): Validity {
    if (creditCardExpiration.isEmpty()) {
      return Validity.POTENTIALLY_VALID
    }

    val monthValidity = isExpirationMonthValid(creditCardExpiration.month, isFocused)
    val yearValidity = isExpirationYearValid(creditCardExpiration.year, isFocused)

    if (monthValidity.isInvalid) {
      return monthValidity
    }

    if (yearValidity.isInvalid) {
      return yearValidity
    }

    if (Validity.POTENTIALLY_VALID in listOf(monthValidity, yearValidity)) {
      return Validity.POTENTIALLY_VALID
    }

    val inputMonthInt = creditCardExpiration.month.toInt()
    val inputYearIntTwoDigits = creditCardExpiration.year.toInt()
    val century = currentYear.floorDiv(100) * 100
    var inputYearInt = inputYearIntTwoDigits + century
    if (inputYearInt < currentYear) {
      // This is for edge-of-century scenarios. If the year is 2099 and the user inputs '01' for the year, this lets us interpret that as 2101.
      inputYearInt += 100
    }

    return if (inputYearInt == currentYear) {
      if (inputMonthInt < currentMonth) {
        Validity.INVALID_EXPIRED
      } else {
        Validity.FULLY_VALID
      }
    } else {
      if (inputYearInt > currentYear + 20) {
        Validity.INVALID_EXPIRED
      } else {
        Validity.FULLY_VALID
      }
    }
  }

  private fun isExpirationMonthValid(inputMonth: String, isFocused: Boolean): Validity {
    if (inputMonth.length > 2 || !inputMonth.isDigitsOnly()) {
      return Validity.INVALID_MONTH
    }

    if (inputMonth in listOf("", "0")) {
      return if (isFocused) {
        Validity.POTENTIALLY_VALID
      } else {
        Validity.INVALID_MONTH
      }
    }

    val month = inputMonth.toInt()
    if (month in 1..12) {
      return Validity.FULLY_VALID
    }

    return Validity.INVALID_MONTH
  }

  private fun isExpirationYearValid(inputYear: String, isFocused: Boolean): Validity {
    if (inputYear.length > 2 || !inputYear.isDigitsOnly()) {
      return Validity.INVALID_YEAR
    }

    if (inputYear.length < 2) {
      return if (isFocused) {
        Validity.POTENTIALLY_VALID
      } else if (inputYear.isEmpty()) {
        Validity.INVALID_MISSING_YEAR
      } else {
        Validity.INVALID_YEAR
      }
    }

    return Validity.FULLY_VALID
  }

  enum class Validity(val isInvalid: Boolean) {
    INVALID_EXPIRED(true),
    INVALID_MISSING_YEAR(true),
    INVALID_MONTH(true),
    INVALID_YEAR(true),
    POTENTIALLY_VALID(false),
    FULLY_VALID(false)
  }
}
