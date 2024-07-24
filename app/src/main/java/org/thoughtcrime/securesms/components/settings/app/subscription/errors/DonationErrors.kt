package org.thoughtcrime.securesms.components.settings.app.subscription.errors

import androidx.annotation.StringRes
import org.signal.donations.InAppPaymentType
import org.signal.donations.StripeDeclineCode
import org.signal.donations.StripeFailureCode
import org.thoughtcrime.securesms.R

@StringRes
fun StripeFailureCode.mapToErrorStringResource(inAppPaymentType: InAppPaymentType): Int {
  return when (this) {
    is StripeFailureCode.Known -> when (this.code) {
      StripeFailureCode.Code.REFER_TO_CUSTOMER -> R.string.StripeFailureCode__verify_your_bank_details_are_correct
      StripeFailureCode.Code.INSUFFICIENT_FUNDS -> R.string.StripeFailureCode__the_bank_account_provided
      StripeFailureCode.Code.DEBIT_DISPUTED -> R.string.StripeFailureCode__verify_your_bank_details_are_correct
      StripeFailureCode.Code.AUTHORIZATION_REVOKED -> InAppPaymentErrorStrings.getStripeFailureCodeAuthorizationRevokedErrorMessage(inAppPaymentType)
      StripeFailureCode.Code.DEBIT_NOT_AUTHORIZED -> InAppPaymentErrorStrings.getStripeFailureCodeAuthorizationRevokedErrorMessage(inAppPaymentType)
      StripeFailureCode.Code.ACCOUNT_CLOSED -> R.string.StripeFailureCode__the_bank_details_provided_could_not_be_processed
      StripeFailureCode.Code.BANK_ACCOUNT_RESTRICTED -> R.string.StripeFailureCode__the_bank_details_provided_could_not_be_processed
      StripeFailureCode.Code.DEBIT_AUTHORIZATION_NOT_MATCH -> InAppPaymentErrorStrings.getStripeFailureCodeDebitAuthorizationNotMatchErrorMessage(inAppPaymentType)
      StripeFailureCode.Code.RECIPIENT_DECEASED -> R.string.StripeFailureCode__the_bank_details_provided_could_not_be_processed
      StripeFailureCode.Code.BRANCH_DOES_NOT_EXIST -> R.string.StripeFailureCode__verify_your_bank_details_are_correct
      StripeFailureCode.Code.INCORRECT_ACCOUNT_HOLDER_NAME -> R.string.StripeFailureCode__verify_your_bank_details_are_correct
      StripeFailureCode.Code.INVALID_ACCOUNT_NUMBER -> R.string.StripeFailureCode__verify_your_bank_details_are_correct
      StripeFailureCode.Code.GENERIC_COULD_NOT_PROCESS -> R.string.StripeFailureCode__verify_your_bank_details_are_correct
    }
    is StripeFailureCode.Unknown -> R.string.StripeFailureCode__verify_your_bank_details_are_correct
  }
}

@StringRes
fun StripeDeclineCode.mapToErrorStringResource(): Int {
  return when (this) {
    is StripeDeclineCode.Known -> when (this.code) {
      StripeDeclineCode.Code.APPROVE_WITH_ID -> R.string.DeclineCode__verify_your_card_details_are_correct_and_try_again
      StripeDeclineCode.Code.CALL_ISSUER -> R.string.DeclineCode__verify_your_card_details_are_correct_and_try_again_if_the_problem_continues
      StripeDeclineCode.Code.CARD_NOT_SUPPORTED -> R.string.DeclineCode__your_card_does_not_support_this_type_of_purchase
      StripeDeclineCode.Code.EXPIRED_CARD -> R.string.DeclineCode__your_card_has_expired_verify_your_card_details
      StripeDeclineCode.Code.INCORRECT_NUMBER -> R.string.DeclineCode__your_card_number_is_incorrect_verify_your_card_details
      StripeDeclineCode.Code.INCORRECT_CVC -> R.string.DeclineCode__your_cards_cvc_number_is_incorrect_verify_your_card_details
      StripeDeclineCode.Code.INSUFFICIENT_FUNDS -> R.string.DeclineCode__your_card_does_not_have_sufficient_funds
      StripeDeclineCode.Code.INVALID_CVC -> R.string.DeclineCode__your_cards_cvc_number_is_incorrect_verify_your_card_details
      StripeDeclineCode.Code.INVALID_EXPIRY_MONTH -> R.string.DeclineCode__the_expiration_month_on_your_card_is_incorrect
      StripeDeclineCode.Code.INVALID_EXPIRY_YEAR -> R.string.DeclineCode__the_expiration_year_on_your_card_is_incorrect
      StripeDeclineCode.Code.INVALID_NUMBER -> R.string.DeclineCode__your_card_number_is_incorrect_verify_your_card_details
      StripeDeclineCode.Code.ISSUER_NOT_AVAILABLE -> R.string.DeclineCode__try_completing_the_payment_again
      StripeDeclineCode.Code.PROCESSING_ERROR -> R.string.DeclineCode__try_again
      StripeDeclineCode.Code.REENTER_TRANSACTION -> R.string.DeclineCode__try_again
      else -> R.string.DeclineCode__try_another_payment_method_or_contact_your_bank
    }
    else -> R.string.DeclineCode__try_another_payment_method_or_contact_your_bank
  }
}
