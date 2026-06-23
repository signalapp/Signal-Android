package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import android.text.TextUtils
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CreditCardCodeValidatorTest(
  private val code: String,
  private val cardType: CreditCardType,
  private val isFocused: Boolean,
  private val validity: CreditCardCodeValidator.Validity
) {

  @Before
  fun setUp() {
    mockkStatic(TextUtils::class)
    every { TextUtils.isDigitsOnly(any()) } answers { firstArg<CharSequence>().all { it.isDigit() } }
  }

  @After
  fun tearDown() {
    unmockkStatic(TextUtils::class)
  }

  @Test
  fun getValidity() {
    assertEquals(validity, CreditCardCodeValidator.getValidity(code, cardType, isFocused))
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{index}: getValidity(..) = {0}, {1}, {2}, {3}")
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
