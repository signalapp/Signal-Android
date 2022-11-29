package org.signal.donations

enum class StripePaymentSourceType(val code: String) {
  CREDIT_CARD("credit_card"),
  GOOGLE_PAY("google_pay");

  companion object {
    fun fromCode(code: String?): StripePaymentSourceType {
      return values().firstOrNull { it.code == code } ?: GOOGLE_PAY
    }
  }
}
