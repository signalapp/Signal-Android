package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = Application::class)
class CreditCardNumberValidatorTest(
  private val creditCardNumber: String,
  private val creditCardNumberFieldFocused: Boolean,
  private val validity: CreditCardNumberValidator.Validity
) {

  @Test
  fun getValidity() {
    assertEquals(validity, CreditCardNumberValidator.getValidity(creditCardNumber, creditCardNumberFieldFocused))
  }

  companion object {
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: getValidity(..) = {0}, {1}, {2}")
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
