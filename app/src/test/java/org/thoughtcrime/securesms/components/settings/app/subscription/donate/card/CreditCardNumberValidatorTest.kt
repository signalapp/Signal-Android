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
class CreditCardNumberValidatorTest(
  private val creditCardNumber: String,
  private val creditCardNumberFieldFocused: Boolean,
  private val validity: CreditCardNumberValidator.Validity
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
    assertEquals(validity, CreditCardNumberValidator.getValidity(creditCardNumber, creditCardNumberFieldFocused))
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{index}: getValidity(..) = {0}, {1}, {2}")
    fun data(): Iterable<Array<Any>> = arrayListOf(
      arrayOf("", false, CreditCardNumberValidator.Validity.POTENTIALLY_VALID),
      arrayOf("4", false, CreditCardNumberValidator.Validity.POTENTIALLY_VALID),
      arrayOf("42", false, CreditCardNumberValidator.Validity.POTENTIALLY_VALID),
      arrayOf("42424242424", false, CreditCardNumberValidator.Validity.POTENTIALLY_VALID),
      arrayOf("424242424242", false, CreditCardNumberValidator.Validity.FULLY_VALID),
      arrayOf("424242424242424242", false, CreditCardNumberValidator.Validity.FULLY_VALID),
      arrayOf("4242424242424", false, CreditCardNumberValidator.Validity.INVALID),
      arrayOf("4242424242424", true, CreditCardNumberValidator.Validity.POTENTIALLY_VALID),
      arrayOf("6200000000000004", false, CreditCardNumberValidator.Validity.FULLY_VALID),
      arrayOf("6200000000000005", false, CreditCardNumberValidator.Validity.FULLY_VALID),
      arrayOf("42424242424242424242", false, CreditCardNumberValidator.Validity.INVALID),
      arrayOf("X", false, CreditCardNumberValidator.Validity.INVALID),
      arrayOf("42X", false, CreditCardNumberValidator.Validity.INVALID),
      arrayOf("424242424242X", false, CreditCardNumberValidator.Validity.INVALID)
    )
  }
}
