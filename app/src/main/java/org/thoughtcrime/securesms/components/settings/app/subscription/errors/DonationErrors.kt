package org.thoughtcrime.securesms.components.settings.app.subscription.errors

import androidx.annotation.StringRes
import org.signal.donations.StripeDeclineCode
import org.thoughtcrime.securesms.R

@StringRes
fun StripeDeclineCode.mapToErrorStringResource(): Int {
  return when (this) {
    is StripeDeclineCode.Known -> when (this.code) {
      StripeDeclineCode.Code.APPROVE_WITH_ID -> R.string.DeclineCode__verify_your_payment_method_is_up_to_date_in_google_pay_and_try_again
      StripeDeclineCode.Code.CALL_ISSUER -> R.string.DeclineCode__verify_your_payment_method_is_up_to_date_in_google_pay_and_try_again_if_the_problem
      StripeDeclineCode.Code.CARD_NOT_SUPPORTED -> R.string.DeclineCode__your_card_does_not_support_this_type_of_purchase
      StripeDeclineCode.Code.EXPIRED_CARD -> R.string.DeclineCode__your_card_has_expired
      StripeDeclineCode.Code.INCORRECT_NUMBER -> R.string.DeclineCode__your_card_number_is_incorrect
      StripeDeclineCode.Code.INCORRECT_CVC -> R.string.DeclineCode__your_cards_cvc_number_is_incorrect
      StripeDeclineCode.Code.INSUFFICIENT_FUNDS -> R.string.DeclineCode__your_card_does_not_have_sufficient_funds
      StripeDeclineCode.Code.INVALID_CVC -> R.string.DeclineCode__your_cards_cvc_number_is_incorrect
      StripeDeclineCode.Code.INVALID_EXPIRY_MONTH -> R.string.DeclineCode__the_expiration_month
      StripeDeclineCode.Code.INVALID_EXPIRY_YEAR -> R.string.DeclineCode__the_expiration_year
      StripeDeclineCode.Code.INVALID_NUMBER -> R.string.DeclineCode__your_card_number_is_incorrect
      StripeDeclineCode.Code.ISSUER_NOT_AVAILABLE -> R.string.DeclineCode__try_completing_the_payment_again
      StripeDeclineCode.Code.PROCESSING_ERROR -> R.string.DeclineCode__try_again
      StripeDeclineCode.Code.REENTER_TRANSACTION -> R.string.DeclineCode__try_again
      else -> R.string.DeclineCode__try_another_payment_method_or_contact_your_bank
    }
    else -> R.string.DeclineCode__try_another_payment_method_or_contact_your_bank
  }
}

fun StripeDeclineCode.shouldRouteToGooglePay(): Boolean {
  return when (this) {
    is StripeDeclineCode.Known -> when (this.code) {
      StripeDeclineCode.Code.APPROVE_WITH_ID -> true
      StripeDeclineCode.Code.CALL_ISSUER -> true
      StripeDeclineCode.Code.CARD_NOT_SUPPORTED -> false
      StripeDeclineCode.Code.EXPIRED_CARD -> true
      StripeDeclineCode.Code.INCORRECT_NUMBER -> true
      StripeDeclineCode.Code.INCORRECT_CVC -> true
      StripeDeclineCode.Code.INSUFFICIENT_FUNDS -> false
      StripeDeclineCode.Code.INVALID_CVC -> true
      StripeDeclineCode.Code.INVALID_EXPIRY_MONTH -> true
      StripeDeclineCode.Code.INVALID_EXPIRY_YEAR -> true
      StripeDeclineCode.Code.INVALID_NUMBER -> true
      StripeDeclineCode.Code.ISSUER_NOT_AVAILABLE -> false
      StripeDeclineCode.Code.PROCESSING_ERROR -> false
      StripeDeclineCode.Code.REENTER_TRANSACTION -> false
      else -> false
    }
    else -> false
  }
}
