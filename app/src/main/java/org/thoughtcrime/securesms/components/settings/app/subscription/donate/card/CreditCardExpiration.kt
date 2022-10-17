package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

data class CreditCardExpiration(
  val month: String = "",
  val year: String = ""
) {

  fun isEmpty(): Boolean {
    return month.isEmpty() && year.isEmpty()
  }

  companion object {
    fun fromInput(expiration: String): CreditCardExpiration {
      val expirationParts = expiration.split("/", limit = 2)
      val month = expirationParts.first()
      val year = expirationParts.drop(1).firstOrNull() ?: ""

      return CreditCardExpiration(month, year)
    }
  }
}
