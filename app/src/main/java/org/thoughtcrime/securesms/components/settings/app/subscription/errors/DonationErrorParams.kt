package org.thoughtcrime.securesms.components.settings.app.subscription.errors

import android.content.Context
import androidx.annotation.StringRes
import org.signal.donations.PaymentSourceType
import org.signal.donations.StripeDeclineCode
import org.signal.donations.StripeFailureCode
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
        is DonationError.GiftRecipientVerificationError -> getVerificationErrorParams(context, throwable, callback)
        is DonationError.PaymentSetupError.StripeDeclinedError -> getStripeDeclinedErrorParams(context, throwable, callback)
        is DonationError.PaymentSetupError.StripeFailureCodeError -> getStripeFailureCodeErrorParams(context, throwable, callback)
        is DonationError.PaymentSetupError.PayPalDeclinedError -> getPayPalDeclinedErrorParams(context, throwable, callback)
        is DonationError.PaymentSetupError -> getGenericPaymentSetupErrorParams(context, callback)

        is DonationError.BadgeRedemptionError.DonationPending -> DonationErrorParams(
          title = R.string.DonationsErrors__still_processing,
          message = R.string.DonationsErrors__your_payment_is_still,
          positiveAction = callback.onOk(context),
          negativeAction = null
        )

        is DonationError.BadgeRedemptionError.TimeoutWaitingForTokenError -> DonationErrorParams(
          title = R.string.DonationsErrors__still_processing,
          message = R.string.DonationsErrors__your_payment_is_still,
          positiveAction = callback.onOk(context),
          negativeAction = null
        )

        is DonationError.BadgeRedemptionError.FailedToValidateCredentialError -> DonationErrorParams(
          title = R.string.DonationsErrors__failed_to_validate_badge,
          message = R.string.DonationsErrors__could_not_validate,
          positiveAction = callback.onContactSupport(context),
          negativeAction = null
        )

        is DonationError.BadgeRedemptionError.GenericError -> getGenericRedemptionError(context, throwable, callback)
        else -> DonationErrorParams(
          title = R.string.DonationsErrors__couldnt_add_badge,
          message = R.string.DonationsErrors__your_badge_could_not,
          positiveAction = callback.onContactSupport(context),
          negativeAction = null
        )
      }
    }

    private fun <V> getGenericRedemptionError(context: Context, genericError: DonationError.BadgeRedemptionError.GenericError, callback: Callback<V>): DonationErrorParams<V> {
      return when (genericError.source) {
        DonationErrorSource.GIFT -> DonationErrorParams(
          title = R.string.DonationsErrors__donation_failed,
          message = R.string.DonationsErrors__your_payment_was_processed_but,
          positiveAction = callback.onContactSupport(context),
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

    private fun <V> getVerificationErrorParams(context: Context, verificationError: DonationError.GiftRecipientVerificationError, callback: Callback<V>): DonationErrorParams<V> {
      return when (verificationError) {
        is DonationError.GiftRecipientVerificationError.FailedToFetchProfile -> DonationErrorParams(
          title = R.string.DonationsErrors__cannot_send_donation,
          message = R.string.DonationsErrors__your_donation_could_not_be_sent,
          positiveAction = callback.onOk(context),
          negativeAction = null
        )

        else -> DonationErrorParams(
          title = R.string.DonationsErrors__cannot_send_donation,
          message = R.string.DonationsErrors__this_user_cant_receive_donations_until,
          positiveAction = callback.onOk(context),
          negativeAction = null
        )
      }
    }

    private fun <V> getPayPalDeclinedErrorParams(context: Context, declinedError: DonationError.PaymentSetupError.PayPalDeclinedError, callback: Callback<V>): DonationErrorParams<V> {
      return when (declinedError.code) {
        PayPalDeclineCode.KnownCode.DECLINED -> getLearnMoreParams(context, callback, R.string.DeclineCode__try_another_payment_method_or_contact_your_bank_for_more_information_if_this_was_a_paypal)
        else -> getLearnMoreParams(context, callback, R.string.DeclineCode__try_another_payment_method_or_contact_your_bank)
      }
    }

    private fun <V> getStripeDeclinedErrorParams(context: Context, declinedError: DonationError.PaymentSetupError.StripeDeclinedError, callback: Callback<V>): DonationErrorParams<V> {
      if (!declinedError.method.hasDeclineCodeSupport()) {
        return getGenericPaymentSetupErrorParams(context, callback)
      }

      fun unexpectedDeclinedError(declinedError: DonationError.PaymentSetupError.StripeDeclinedError): Nothing {
        error("Unexpected declined error: ${declinedError.declineCode} during ${declinedError.method} processing.")
      }

      val getStripeDeclineCodePositiveActionParams: (Context, Callback<V>, Int) -> DonationErrorParams<V> = when (declinedError.method) {
        PaymentSourceType.Stripe.CreditCard -> this::getTryCreditCardAgainParams
        PaymentSourceType.Stripe.GooglePay -> this::getGoToGooglePayParams
        else -> this::getLearnMoreParams
      }

      return when (declinedError.declineCode) {
        is StripeDeclineCode.Known -> when (declinedError.declineCode.code) {
          StripeDeclineCode.Code.APPROVE_WITH_ID -> getStripeDeclineCodePositiveActionParams(
            context,
            callback,
            when (declinedError.method) {
              PaymentSourceType.Stripe.CreditCard -> R.string.DeclineCode__verify_your_card_details_are_correct_and_try_again
              PaymentSourceType.Stripe.GooglePay -> R.string.DeclineCode__verify_your_payment_method_is_up_to_date_in_google_pay_and_try_again
              else -> unexpectedDeclinedError(declinedError)
            }
          )

          StripeDeclineCode.Code.CALL_ISSUER -> getStripeDeclineCodePositiveActionParams(
            context,
            callback,
            when (declinedError.method) {
              PaymentSourceType.Stripe.CreditCard -> R.string.DeclineCode__verify_your_card_details_are_correct_and_try_again_if_the_problem_continues
              PaymentSourceType.Stripe.GooglePay -> R.string.DeclineCode__verify_your_payment_method_is_up_to_date_in_google_pay_and_try_again_if_the_problem
              else -> unexpectedDeclinedError(declinedError)
            }
          )

          StripeDeclineCode.Code.CARD_NOT_SUPPORTED -> getLearnMoreParams(context, callback, R.string.DeclineCode__your_card_does_not_support_this_type_of_purchase)
          StripeDeclineCode.Code.EXPIRED_CARD -> getStripeDeclineCodePositiveActionParams(
            context,
            callback,
            when (declinedError.method) {
              PaymentSourceType.Stripe.CreditCard -> R.string.DeclineCode__your_card_has_expired_verify_your_card_details
              PaymentSourceType.Stripe.GooglePay -> R.string.DeclineCode__your_card_has_expired
              else -> unexpectedDeclinedError(declinedError)
            }
          )

          StripeDeclineCode.Code.INCORRECT_NUMBER -> getStripeDeclineCodePositiveActionParams(
            context,
            callback,
            when (declinedError.method) {
              PaymentSourceType.Stripe.CreditCard -> R.string.DeclineCode__your_card_number_is_incorrect_verify_your_card_details
              PaymentSourceType.Stripe.GooglePay -> R.string.DeclineCode__your_card_number_is_incorrect
              else -> unexpectedDeclinedError(declinedError)
            }
          )

          StripeDeclineCode.Code.INCORRECT_CVC -> getStripeDeclineCodePositiveActionParams(
            context,
            callback,
            when (declinedError.method) {
              PaymentSourceType.Stripe.CreditCard -> R.string.DeclineCode__your_cards_cvc_number_is_incorrect_verify_your_card_details
              PaymentSourceType.Stripe.GooglePay -> R.string.DeclineCode__your_cards_cvc_number_is_incorrect
              else -> unexpectedDeclinedError(declinedError)
            }
          )

          StripeDeclineCode.Code.INSUFFICIENT_FUNDS -> getLearnMoreParams(context, callback, R.string.DeclineCode__your_card_does_not_have_sufficient_funds)
          StripeDeclineCode.Code.INVALID_CVC -> getStripeDeclineCodePositiveActionParams(
            context,
            callback,
            when (declinedError.method) {
              PaymentSourceType.Stripe.CreditCard -> R.string.DeclineCode__your_cards_cvc_number_is_incorrect_verify_your_card_details
              PaymentSourceType.Stripe.GooglePay -> R.string.DeclineCode__your_cards_cvc_number_is_incorrect
              else -> unexpectedDeclinedError(declinedError)
            }
          )

          StripeDeclineCode.Code.INVALID_EXPIRY_MONTH -> getStripeDeclineCodePositiveActionParams(
            context,
            callback,
            when (declinedError.method) {
              PaymentSourceType.Stripe.CreditCard -> R.string.DeclineCode__the_expiration_month_on_your_card_is_incorrect
              PaymentSourceType.Stripe.GooglePay -> R.string.DeclineCode__the_expiration_month
              else -> unexpectedDeclinedError(declinedError)
            }
          )

          StripeDeclineCode.Code.INVALID_EXPIRY_YEAR -> getStripeDeclineCodePositiveActionParams(
            context,
            callback,
            when (declinedError.method) {
              PaymentSourceType.Stripe.CreditCard -> R.string.DeclineCode__the_expiration_year_on_your_card_is_incorrect
              PaymentSourceType.Stripe.GooglePay -> R.string.DeclineCode__the_expiration_year
              else -> unexpectedDeclinedError(declinedError)
            }
          )

          StripeDeclineCode.Code.INVALID_NUMBER -> getStripeDeclineCodePositiveActionParams(
            context,
            callback,
            when (declinedError.method) {
              PaymentSourceType.Stripe.CreditCard -> R.string.DeclineCode__your_card_number_is_incorrect_verify_your_card_details
              PaymentSourceType.Stripe.GooglePay -> R.string.DeclineCode__your_card_number_is_incorrect
              else -> unexpectedDeclinedError(declinedError)
            }
          )

          StripeDeclineCode.Code.ISSUER_NOT_AVAILABLE -> getLearnMoreParams(context, callback, R.string.DeclineCode__try_completing_the_payment_again)
          StripeDeclineCode.Code.PROCESSING_ERROR -> getLearnMoreParams(context, callback, R.string.DeclineCode__try_again)
          StripeDeclineCode.Code.REENTER_TRANSACTION -> getLearnMoreParams(context, callback, R.string.DeclineCode__try_again)
          else -> getLearnMoreParams(context, callback, R.string.DeclineCode__try_another_payment_method_or_contact_your_bank)
        }

        else -> getLearnMoreParams(context, callback, R.string.DeclineCode__try_another_payment_method_or_contact_your_bank)
      }
    }

    private fun <V> getStripeFailureCodeErrorParams(context: Context, failureCodeError: DonationError.PaymentSetupError.StripeFailureCodeError, callback: Callback<V>): DonationErrorParams<V> {
      if (!failureCodeError.method.hasFailureCodeSupport()) {
        return getGenericPaymentSetupErrorParams(context, callback)
      }

      return when (failureCodeError.failureCode) {
        is StripeFailureCode.Known -> {
          val errorText = failureCodeError.failureCode.mapToErrorStringResource()
          when (failureCodeError.failureCode.code) {
            StripeFailureCode.Code.REFER_TO_CUSTOMER -> getTryBankTransferAgainParams(context, callback, errorText)
            StripeFailureCode.Code.INSUFFICIENT_FUNDS -> getLearnMoreParams(context, callback, errorText)
            StripeFailureCode.Code.DEBIT_DISPUTED -> getLearnMoreParams(context, callback, errorText)
            StripeFailureCode.Code.AUTHORIZATION_REVOKED -> getLearnMoreParams(context, callback, errorText)
            StripeFailureCode.Code.DEBIT_NOT_AUTHORIZED -> getLearnMoreParams(context, callback, errorText)
            StripeFailureCode.Code.ACCOUNT_CLOSED -> getLearnMoreParams(context, callback, errorText)
            StripeFailureCode.Code.BANK_ACCOUNT_RESTRICTED -> getLearnMoreParams(context, callback, errorText)
            StripeFailureCode.Code.DEBIT_AUTHORIZATION_NOT_MATCH -> getLearnMoreParams(context, callback, errorText)
            StripeFailureCode.Code.RECIPIENT_DECEASED -> getLearnMoreParams(context, callback, errorText)
            StripeFailureCode.Code.BRANCH_DOES_NOT_EXIST -> getTryBankTransferAgainParams(context, callback, errorText)
            StripeFailureCode.Code.INCORRECT_ACCOUNT_HOLDER_NAME -> getTryBankTransferAgainParams(context, callback, errorText)
            StripeFailureCode.Code.INVALID_ACCOUNT_NUMBER -> getTryBankTransferAgainParams(context, callback, errorText)
            StripeFailureCode.Code.GENERIC_COULD_NOT_PROCESS -> getTryBankTransferAgainParams(context, callback, errorText)
          }
        }
        is StripeFailureCode.Unknown -> getGenericPaymentSetupErrorParams(context, callback)
      }
    }

    private fun <V> getGenericPaymentSetupErrorParams(context: Context, callback: Callback<V>): DonationErrorParams<V> {
      return DonationErrorParams(
        title = R.string.DonationsErrors__error_processing_payment,
        message = R.string.DonationsErrors__your_payment,
        positiveAction = callback.onOk(context),
        negativeAction = null
      )
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

    private fun <V> getTryCreditCardAgainParams(context: Context, callback: Callback<V>, message: Int): DonationErrorParams<V> {
      return DonationErrorParams(
        title = R.string.DonationsErrors__error_processing_payment,
        message = message,
        positiveAction = callback.onTryCreditCardAgain(context),
        negativeAction = callback.onCancel(context)
      )
    }

    private fun <V> getTryBankTransferAgainParams(context: Context, callback: Callback<V>, message: Int): DonationErrorParams<V> {
      return DonationErrorParams(
        title = R.string.DonationsErrors__error_processing_payment,
        message = message,
        positiveAction = callback.onTryBankTransferAgain(context),
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
    fun onTryCreditCardAgain(context: Context): ErrorAction<V>?
    fun onTryBankTransferAgain(context: Context): ErrorAction<V>?
  }
}
