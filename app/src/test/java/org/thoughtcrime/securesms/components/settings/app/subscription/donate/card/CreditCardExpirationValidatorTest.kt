package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = Application::class)
class CreditCardExpirationValidatorTest(
  private val creditCardExpiration: CreditCardExpiration,
  private val currentYear: Int,
  private val isFocused: Boolean,
  private val validity: CreditCardExpirationValidator.Validity
) {

  @Test
  fun getValidity() {
    assertEquals(validity, CreditCardExpirationValidator.getValidity(creditCardExpiration, 3, currentYear, isFocused))
  }

  companion object {
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: getValidity(..) = {0}, {1}, {2}, {3}, {4}")
    fun data(): Iterable<Array<Any>> = arrayListOf(
      // Unfocused
      arrayOf(CreditCardExpiration("", ""), 2020, false, CreditCardExpirationValidator.Validity.POTENTIALLY_VALID),
      arrayOf(CreditCardExpiration("0", ""), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("1", ""), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MISSING_YEAR),
      arrayOf(CreditCardExpiration("9", ""), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MISSING_YEAR),
      arrayOf(CreditCardExpiration("01", ""), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MISSING_YEAR),
      arrayOf(CreditCardExpiration("09", ""), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MISSING_YEAR),
      arrayOf(CreditCardExpiration("12", ""), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MISSING_YEAR),
      arrayOf(CreditCardExpiration("", "0"), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("", "1"), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("", "9"), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("", "00"), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("", "01"), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("", "99"), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("3", "20"), 2020, false, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("03", "20"), 2020, false, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("4", "20"), 2020, false, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("12", "20"), 2020, false, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("3", "21"), 2020, false, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("3", "40"), 2020, false, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("2", "20"), 2020, false, CreditCardExpirationValidator.Validity.INVALID_EXPIRED),
      arrayOf(CreditCardExpiration("3", "41"), 2020, false, CreditCardExpirationValidator.Validity.INVALID_EXPIRED),
      arrayOf(CreditCardExpiration("3", "41"), 2021, false, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("01", "99"), 2098, false, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("03", "00"), 2099, false, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("04", "00"), 2099, false, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("03", "19"), 2099, false, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("12", "19"), 2099, false, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("00", "20"), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("X", ""), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("1X", ""), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("123", ""), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("", "X"), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("", "2X"), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("", "202"), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("", "2020"), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("X", "X"), 2020, false, CreditCardExpirationValidator.Validity.INVALID_MONTH),

      // Focused
      arrayOf(CreditCardExpiration("", ""), 2020, true, CreditCardExpirationValidator.Validity.POTENTIALLY_VALID),
      arrayOf(CreditCardExpiration("0", ""), 2020, true, CreditCardExpirationValidator.Validity.POTENTIALLY_VALID),
      arrayOf(CreditCardExpiration("1", ""), 2020, true, CreditCardExpirationValidator.Validity.POTENTIALLY_VALID),
      arrayOf(CreditCardExpiration("9", ""), 2020, true, CreditCardExpirationValidator.Validity.POTENTIALLY_VALID),
      arrayOf(CreditCardExpiration("01", ""), 2020, true, CreditCardExpirationValidator.Validity.POTENTIALLY_VALID),
      arrayOf(CreditCardExpiration("09", ""), 2020, true, CreditCardExpirationValidator.Validity.POTENTIALLY_VALID),
      arrayOf(CreditCardExpiration("12", ""), 2020, true, CreditCardExpirationValidator.Validity.POTENTIALLY_VALID),
      arrayOf(CreditCardExpiration("", "0"), 2020, true, CreditCardExpirationValidator.Validity.POTENTIALLY_VALID),
      arrayOf(CreditCardExpiration("", "1"), 2020, true, CreditCardExpirationValidator.Validity.POTENTIALLY_VALID),
      arrayOf(CreditCardExpiration("", "9"), 2020, true, CreditCardExpirationValidator.Validity.POTENTIALLY_VALID),
      arrayOf(CreditCardExpiration("", "00"), 2020, true, CreditCardExpirationValidator.Validity.POTENTIALLY_VALID),
      arrayOf(CreditCardExpiration("", "01"), 2020, true, CreditCardExpirationValidator.Validity.POTENTIALLY_VALID),
      arrayOf(CreditCardExpiration("", "99"), 2020, true, CreditCardExpirationValidator.Validity.POTENTIALLY_VALID),
      arrayOf(CreditCardExpiration("3", "20"), 2020, true, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("03", "20"), 2020, true, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("4", "20"), 2020, true, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("12", "20"), 2020, true, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("3", "21"), 2020, true, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("3", "40"), 2020, true, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("2", "20"), 2020, true, CreditCardExpirationValidator.Validity.INVALID_EXPIRED),
      arrayOf(CreditCardExpiration("3", "41"), 2020, true, CreditCardExpirationValidator.Validity.INVALID_EXPIRED),
      arrayOf(CreditCardExpiration("3", "41"), 2021, true, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("01", "99"), 2098, true, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("03", "00"), 2099, true, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("04", "00"), 2099, true, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("03", "19"), 2099, true, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("12", "19"), 2099, true, CreditCardExpirationValidator.Validity.FULLY_VALID),
      arrayOf(CreditCardExpiration("00", "20"), 2020, true, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("X", ""), 2020, true, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("1X", ""), 2020, true, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("123", ""), 2020, true, CreditCardExpirationValidator.Validity.INVALID_MONTH),
      arrayOf(CreditCardExpiration("", "X"), 2020, true, CreditCardExpirationValidator.Validity.INVALID_YEAR),
      arrayOf(CreditCardExpiration("", "2X"), 2020, true, CreditCardExpirationValidator.Validity.INVALID_YEAR),
      arrayOf(CreditCardExpiration("", "202"), 2020, true, CreditCardExpirationValidator.Validity.INVALID_YEAR),
      arrayOf(CreditCardExpiration("", "2020"), 2020, true, CreditCardExpirationValidator.Validity.INVALID_YEAR),
      arrayOf(CreditCardExpiration("X", "X"), 2020, true, CreditCardExpirationValidator.Validity.INVALID_MONTH)
    )
  }
}
