package org.thoughtcrime.securesms.components.settings.app.subscription.errors

import android.content.Context
import androidx.annotation.StringRes
import org.signal.donations.StripeDeclineCode
import org.thoughtcrime.securesms.R

class DonationErrorParams<V> private constructor(
  @StringRes val title: Int,
  @StringRes val message: Int,
  val positiveAction: ErrorAction<V>?,
  val negativeAction: ErrorAction<V>?
) {
  class ErrorAction<V>(
    @StringRes val label: Int,
    val action: () -> V
  )

  companion object {
    fun <V> create(
      context: Context,
      throwable: Throwable?,
      callback: Callback<V>
    ): DonationErrorParams<V> {
      return when (throwable) {
        is DonationError.PaymentSetupError.DeclinedError -> getDeclinedErrorParams(context, throwable, callback)
        is DonationError.PaymentSetupError -> DonationErrorParams(
          title = R.string.DonationsErrors__error_processing_payment,
          message = R.string.DonationsErrors__your_payment,
          positiveAction = callback.onOk(context),
          negativeAction = null
        )
        is DonationError.BadgeRedemptionError.TimeoutWaitingForTokenError -> DonationErrorParams(
          title = R.string.DonationsErrors__still_processing,
          message = R.string.DonationsErrors__your_payment_is_still,
          positiveAction = callback.onOk(context),
          negativeAction = null
        )
        else -> DonationErrorParams(
          title = R.string.DonationsErrors__couldnt_add_badge,
          message = R.string.DonationsErrors__your_badge_could_not,
          positiveAction = callback.onContactSupport(context),
          negativeAction = null
        )
      }
    }

    private fun <V> getDeclinedErrorParams(context: Context, declinedError: DonationError.PaymentSetupError.DeclinedError, callback: Callback<V>): DonationErrorParams<V> {
      return when (declinedError.declineCode) {
        is StripeDeclineCode.Known -> when (declinedError.declineCode.code) {
          StripeDeclineCode.Code.APPROVE_WITH_ID -> getGoToGooglePayParams(context, callback, R.string.DeclineCode__verify_your_payment_method_is_up_to_date_in_google_pay_and_try_again)
          StripeDeclineCode.Code.CALL_ISSUER -> getGoToGooglePayParams(context, callback, R.string.DeclineCode__verify_your_payment_method_is_up_to_date_in_google_pay_and_try_again_if_the_problem)
          StripeDeclineCode.Code.CARD_NOT_SUPPORTED -> getLearnMoreParams(context, callback, R.string.DeclineCode__your_card_does_not_support_this_type_of_purchase)
          StripeDeclineCode.Code.EXPIRED_CARD -> getGoToGooglePayParams(context, callback, R.string.DeclineCode__your_card_has_expired)
          StripeDeclineCode.Code.INCORRECT_NUMBER -> getGoToGooglePayParams(context, callback, R.string.DeclineCode__your_card_number_is_incorrect)
          StripeDeclineCode.Code.INCORRECT_CVC -> getGoToGooglePayParams(context, callback, R.string.DeclineCode__your_cards_cvc_number_is_incorrect)
          StripeDeclineCode.Code.INSUFFICIENT_FUNDS -> getLearnMoreParams(context, callback, R.string.DeclineCode__your_card_does_not_have_sufficient_funds)
          StripeDeclineCode.Code.INVALID_CVC -> getGoToGooglePayParams(context, callback, R.string.DeclineCode__your_cards_cvc_number_is_incorrect)
          StripeDeclineCode.Code.INVALID_EXPIRY_MONTH -> getGoToGooglePayParams(context, callback, R.string.DeclineCode__the_expiration_month)
          StripeDeclineCode.Code.INVALID_EXPIRY_YEAR -> getGoToGooglePayParams(context, callback, R.string.DeclineCode__the_expiration_year)
          StripeDeclineCode.Code.INVALID_NUMBER -> getGoToGooglePayParams(context, callback, R.string.DeclineCode__your_card_number_is_incorrect)
          StripeDeclineCode.Code.ISSUER_NOT_AVAILABLE -> getLearnMoreParams(context, callback, R.string.DeclineCode__try_completing_the_payment_again)
          StripeDeclineCode.Code.PROCESSING_ERROR -> getLearnMoreParams(context, callback, R.string.DeclineCode__try_again)
          StripeDeclineCode.Code.REENTER_TRANSACTION -> getLearnMoreParams(context, callback, R.string.DeclineCode__try_again)
          else -> getLearnMoreParams(context, callback, R.string.DeclineCode__try_another_payment_method_or_contact_your_bank)
        }
        else -> getLearnMoreParams(context, callback, R.string.DeclineCode__try_another_payment_method_or_contact_your_bank)
      }
    }

    private fun <V> getLearnMoreParams(context: Context, callback: Callback<V>, message: Int): DonationErrorParams<V> {
      return DonationErrorParams(
        title = R.string.DonationsErrors__error_processing_payment,
        message = message,
        positiveAction = callback.onOk(context),
        negativeAction = callback.onLearnMore(context)
      )
    }

    private fun <V> getGoToGooglePayParams(context: Context, callback: Callback<V>, message: Int): DonationErrorParams<V> {
      return DonationErrorParams(
        title = R.string.DonationsErrors__error_processing_payment,
        message = message,
        positiveAction = callback.onGoToGooglePay(context),
        negativeAction = callback.onCancel(context)
      )
    }
  }

  interface Callback<V> {
    fun onOk(context: Context): ErrorAction<V>?
    fun onCancel(context: Context): ErrorAction<V>?
    fun onLearnMore(context: Context): ErrorAction<V>?
    fun onContactSupport(context: Context): ErrorAction<V>?
    fun onGoToGooglePay(context: Context): ErrorAction<V>?
  }
}
