package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

data class CreditCardFormState(
  val focusedField: FocusedField = FocusedField.NONE,
  val number: String = "",
  val expiration: CreditCardExpiration = CreditCardExpiration(),
  val code: String = ""
) {
  enum class FocusedField {
    NONE,
    NUMBER,
    EXPIRATION,
    CODE
  }
}
