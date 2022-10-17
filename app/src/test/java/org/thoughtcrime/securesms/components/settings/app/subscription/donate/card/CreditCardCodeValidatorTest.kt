package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = Application::class)
class CreditCardCodeValidatorTest(
  private val code: String,
  private val cardType: CreditCardType,
  private val isFocused: Boolean,
  private val validity: CreditCardCodeValidator.Validity
) {

  @Test
  fun getValidity() {
    assertEquals(validity, CreditCardCodeValidator.getValidity(code, cardType, isFocused))
  }

  companion object {
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: getValidity(..) = {0}, {1}, {2}, {3}")
    fun data(): Iterable<Array<Any>> = arrayListOf(
      // Unfocused
      arrayOf("", CreditCardType.UNIONPAY, false, CreditCardCodeValidator.Validity.POTENTIALLY_VALID),
      arrayOf("1", CreditCardType.UNIONPAY, false, CreditCardCodeValidator.Validity.TOO_SHORT),
      arrayOf("12", CreditCardType.UNIONPAY, false, CreditCardCodeValidator.Validity.TOO_SHORT),
      arrayOf("123", CreditCardType.UNIONPAY, false, CreditCardCodeValidator.Validity.FULLY_VALID),
      arrayOf("1234", CreditCardType.UNIONPAY, false, CreditCardCodeValidator.Validity.TOO_LONG),
      arrayOf("", CreditCardType.OTHER, false, CreditCardCodeValidator.Validity.POTENTIALLY_VALID),
      arrayOf("1", CreditCardType.OTHER, false, CreditCardCodeValidator.Validity.TOO_SHORT),
      arrayOf("12", CreditCardType.OTHER, false, CreditCardCodeValidator.Validity.TOO_SHORT),
      arrayOf("123", CreditCardType.OTHER, false, CreditCardCodeValidator.Validity.FULLY_VALID),
      arrayOf("1234", CreditCardType.OTHER, false, CreditCardCodeValidator.Validity.TOO_LONG),
      arrayOf("", CreditCardType.AMERICAN_EXPRESS, false, CreditCardCodeValidator.Validity.POTENTIALLY_VALID),
      arrayOf("1", CreditCardType.AMERICAN_EXPRESS, false, CreditCardCodeValidator.Validity.TOO_SHORT),
      arrayOf("12", CreditCardType.AMERICAN_EXPRESS, false, CreditCardCodeValidator.Validity.TOO_SHORT),
      arrayOf("123", CreditCardType.AMERICAN_EXPRESS, false, CreditCardCodeValidator.Validity.TOO_SHORT),
      arrayOf("1234", CreditCardType.AMERICAN_EXPRESS, false, CreditCardCodeValidator.Validity.FULLY_VALID),
      arrayOf("12345", CreditCardType.AMERICAN_EXPRESS, false, CreditCardCodeValidator.Validity.TOO_LONG),

      // Focused
      arrayOf("", CreditCardType.UNIONPAY, true, CreditCardCodeValidator.Validity.POTENTIALLY_VALID),
      arrayOf("1", CreditCardType.UNIONPAY, true, CreditCardCodeValidator.Validity.POTENTIALLY_VALID),
      arrayOf("12", CreditCardType.UNIONPAY, true, CreditCardCodeValidator.Validity.POTENTIALLY_VALID),
      arrayOf("123", CreditCardType.UNIONPAY, true, CreditCardCodeValidator.Validity.FULLY_VALID),
      arrayOf("1234", CreditCardType.UNIONPAY, true, CreditCardCodeValidator.Validity.TOO_LONG),
      arrayOf("", CreditCardType.OTHER, true, CreditCardCodeValidator.Validity.POTENTIALLY_VALID),
      arrayOf("1", CreditCardType.OTHER, true, CreditCardCodeValidator.Validity.POTENTIALLY_VALID),
      arrayOf("12", CreditCardType.OTHER, true, CreditCardCodeValidator.Validity.POTENTIALLY_VALID),
      arrayOf("123", CreditCardType.OTHER, true, CreditCardCodeValidator.Validity.FULLY_VALID),
      arrayOf("1234", CreditCardType.OTHER, true, CreditCardCodeValidator.Validity.TOO_LONG),
      arrayOf("", CreditCardType.AMERICAN_EXPRESS, true, CreditCardCodeValidator.Validity.POTENTIALLY_VALID),
      arrayOf("1", CreditCardType.AMERICAN_EXPRESS, true, CreditCardCodeValidator.Validity.POTENTIALLY_VALID),
      arrayOf("12", CreditCardType.AMERICAN_EXPRESS, true, CreditCardCodeValidator.Validity.POTENTIALLY_VALID),
      arrayOf("123", CreditCardType.AMERICAN_EXPRESS, true, CreditCardCodeValidator.Validity.POTENTIALLY_VALID),
      arrayOf("1234", CreditCardType.AMERICAN_EXPRESS, true, CreditCardCodeValidator.Validity.FULLY_VALID),
      arrayOf("12345", CreditCardType.AMERICAN_EXPRESS, true, CreditCardCodeValidator.Validity.TOO_LONG)
    )
  }
}
