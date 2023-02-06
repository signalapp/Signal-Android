package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CreditCardTypeTest(
  private val creditCardNumber: String,
  private val creditCardType: CreditCardType
) {

  @Test
  fun fromCardNumber() {
    assertEquals(creditCardType, CreditCardType.fromCardNumber(cardNumber = creditCardNumber))
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{index}: fromCardNumber(..) = {0}, {1}")
    fun data(): Iterable<Array<Any>> = arrayListOf(
      arrayOf("34", CreditCardType.AMERICAN_EXPRESS),
      arrayOf("37", CreditCardType.AMERICAN_EXPRESS),
      arrayOf("343452000000306", CreditCardType.AMERICAN_EXPRESS),
      arrayOf("371449635398431", CreditCardType.AMERICAN_EXPRESS),
      arrayOf("378282246310005", CreditCardType.AMERICAN_EXPRESS),
      arrayOf("62", CreditCardType.UNIONPAY),
      arrayOf("81", CreditCardType.UNIONPAY),
      arrayOf("6200000000000004", CreditCardType.UNIONPAY),
      arrayOf("", CreditCardType.OTHER),
      arrayOf("X", CreditCardType.OTHER),
      arrayOf("4", CreditCardType.OTHER),
      arrayOf("4111111111111111", CreditCardType.OTHER),
      arrayOf("4242424242424242", CreditCardType.OTHER),
      arrayOf("5555555555554444", CreditCardType.OTHER),
      arrayOf("5555555555554444", CreditCardType.OTHER),
      arrayOf("2223003122003222", CreditCardType.OTHER),
      arrayOf("6011111111111117", CreditCardType.OTHER),
      arrayOf("3056930009020004", CreditCardType.OTHER),
      arrayOf("3566002020360505", CreditCardType.OTHER)
    )
  }
}
