package org.signal.donations

sealed class PaymentSourceType {
  abstract val code: String

  object Unknown : PaymentSourceType() {
    override val code: String = Codes.UNKNOWN.code
  }

  object PayPal : PaymentSourceType() {
    override val code: String = Codes.PAY_PAL.code
  }

  sealed class Stripe(override val code: String, val paymentMethod: String) : PaymentSourceType() {
    object CreditCard : Stripe(Codes.CREDIT_CARD.code, "CARD")
    object GooglePay : Stripe(Codes.GOOGLE_PAY.code, "CARD")
    object SEPADebit : Stripe(Codes.SEPA_DEBIT.code, "SEPA_DEBIT")
  }

  private enum class Codes(val code: String) {
    UNKNOWN("unknown"),
    PAY_PAL("paypal"),
    CREDIT_CARD("credit_card"),
    GOOGLE_PAY("google_pay"),
    SEPA_DEBIT("sepa_debit")
  }

  companion object {
    fun fromCode(code: String?): PaymentSourceType {
      return when (Codes.values().firstOrNull { it.code == code } ?: Codes.UNKNOWN) {
        Codes.UNKNOWN -> Unknown
        Codes.PAY_PAL -> PayPal
        Codes.CREDIT_CARD -> Stripe.CreditCard
        Codes.GOOGLE_PAY -> Stripe.GooglePay
        Codes.SEPA_DEBIT -> Stripe.SEPADebit
      }
    }
  }
}
