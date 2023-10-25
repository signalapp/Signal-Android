/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.donations

/**
 * Bank Transfer failure codes, as detailed here:
 * https://stripe.com/docs/payments/sepa-debit#failed-payments
 */
sealed class StripeFailureCode(val rawCode: String) {
  data class Known(val code: Code) : StripeFailureCode(code.code)
  data class Unknown(val code: String) : StripeFailureCode(code)

  val isKnown get() = this is Known
  enum class Code(val code: String) {
    REFER_TO_CUSTOMER("refer_to_customer"),
    INSUFFICIENT_FUNDS("insufficient_funds"),
    DEBIT_DISPUTED("debit_disputed"),
    AUTHORIZATION_REVOKED("authorization_revoked"),
    DEBIT_NOT_AUTHORIZED("debit_not_authorized"),
    ACCOUNT_CLOSED("account_closed"),
    BANK_ACCOUNT_RESTRICTED("bank_account_restricted"),
    DEBIT_AUTHORIZATION_NOT_MATCH("debit_authorization_not_match"),
    RECIPIENT_DECEASED("recipient_deceased"),
    BRANCH_DOES_NOT_EXIST("branch_does_not_exist"),
    INCORRECT_ACCOUNT_HOLDER_NAME("incorrect_account_holder_name"),
    INVALID_ACCOUNT_NUMBER("invalid_account_number"),
    GENERIC_COULD_NOT_PROCESS("generic_could_not_process")
  }

  companion object {
    fun getFromCode(code: String?): StripeFailureCode {
      if (code == null) {
        return Unknown("null")
      }

      val typedCode: Code? = Code.values().firstOrNull { it.code == code }
      return typedCode?.let { Known(typedCode) } ?: Unknown(code)
    }
  }
}
