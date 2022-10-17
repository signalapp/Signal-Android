package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

enum class CreditCardType {
  AMERICAN_EXPRESS,
  UNIONPAY,
  OTHER;

  companion object {
    private val AMERICAN_EXPRESS_PREFIXES = listOf("34", "37")
    private val UNIONPAY_PREFIXES = listOf("62", "81")

    fun fromCardNumber(cardNumber: String): CreditCardType {
      return when {
        AMERICAN_EXPRESS_PREFIXES.any { cardNumber.startsWith(it) } -> AMERICAN_EXPRESS
        UNIONPAY_PREFIXES.any { cardNumber.startsWith(it) } -> UNIONPAY
        else -> OTHER
      }
    }
  }
}
