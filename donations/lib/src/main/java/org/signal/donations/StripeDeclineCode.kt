package org.signal.donations

/**
 * Stripe Payment Processor decline codes
 */
sealed class StripeDeclineCode(val rawCode: String) {

  data class Known(val code: Code) : StripeDeclineCode(code.code)
  data class Unknown(val code: String) : StripeDeclineCode(code)

  fun isKnown(): Boolean = this is Known

  enum class Code(val code: String) {
    AUTHENTICATION_REQUIRED("authentication_required"),
    APPROVE_WITH_ID("approve_with_id"),
    CALL_ISSUER("call_issuer"),
    CARD_NOT_SUPPORTED("card_not_supported"),
    CARD_VELOCITY_EXCEEDED("card_velocity_exceeded"),
    CURRENCY_NOT_SUPPORTED("currency_not_supported"),
    DO_NOT_HONOR("do_not_honor"),
    DO_NOT_TRY_AGAIN("do_not_try_again"),
    DUPLICATE_TRANSACTION("duplicate_transaction"),
    EXPIRED_CARD("expired_card"),
    FRAUDULENT("fraudulent"),
    GENERIC_DECLINE("generic_decline"),
    INCORRECT_NUMBER("incorrect_number"),
    INCORRECT_CVC("incorrect_cvc"),
    INSUFFICIENT_FUNDS("insufficient_funds"),
    INVALID_ACCOUNT("invalid_account"),
    INVALID_AMOUNT("invalid_amount"),
    INVALID_CVC("invalid_cvc"),
    INVALID_EXPIRY_MONTH("invalid_expiry_month"),
    INVALID_EXPIRY_YEAR("invalid_expiry_year"),
    INVALID_NUMBER("invalid_number"),
    ISSUER_NOT_AVAILABLE("issuer_not_available"),
    LOST_CARD("lost_card"),
    MERCHANT_BLACKLIST("merchant_blacklist"),
    NEW_ACCOUNT_INFORMATION_AVAILABLE("new_account_information_available"),
    NO_ACTION_TAKEN("no_action_taken"),
    NOT_PERMITTED("not_permitted"),
    PROCESSING_ERROR("processing_error"),
    REENTER_TRANSACTION("reenter_transaction"),
    RESTRICTED_CARD("restricted_card"),
    REVOCATION_OF_ALL_AUTHORIZATIONS("revocation_of_all_authorizations"),
    REVOCATION_OF_AUTHORIZATION("revocation_of_authorization"),
    SECURITY_VIOLATION("security_violation"),
    SERVICE_NOT_ALLOWED("service_not_allowed"),
    STOLEN_CARD("stolen_card"),
    STOP_PAYMENT_ORDER("stop_payment_order"),
    TRANSACTION_NOT_ALLOWED("transaction_not_allowed"),
    TRY_AGAIN_LATER("try_again_later"),
    WITHDRAWAL_COUNT_LIMIT_EXCEEDED("withdrawal_count_limit_exceeded")
  }

  companion object {
    fun getFromCode(code: String?): StripeDeclineCode {
      if (code == null) {
        return Unknown("null")
      }

      val typedCode: Code? = Code.entries.firstOrNull { it.code == code }
      return typedCode?.let { Known(typedCode) } ?: Unknown(code)
    }
  }
}
