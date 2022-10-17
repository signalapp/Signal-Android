package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

data class CreditCardValidationState(
  val type: CreditCardType,
  val numberValidity: CreditCardNumberValidator.Validity,
  val expirationValidity: CreditCardExpirationValidator.Validity,
  val codeValidity: CreditCardCodeValidator.Validity
)
