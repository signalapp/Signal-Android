package org.thoughtcrime.securesms.components.settings.app.subscription.errors

import android.content.Context
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.core.util.logging.Log
import org.signal.donations.PaymentSourceType
import org.signal.donations.StripeDeclineCode
import org.signal.donations.StripeError
import org.signal.donations.StripeFailureCode
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.model.databaseprotos.DonationErrorValue

/**
 * @deprecated Replaced with InAppDonationData.Error
 *
 * This needs to remain until all the old jobs are through people's systems (90 days from release + timeout)
 */
sealed class DonationError(val source: DonationErrorSource, cause: Throwable) : Exception(cause) {

  /**
   * Google Pay errors, which happen well before a user would ever be charged.
   */
  sealed class GooglePayError(source: DonationErrorSource, cause: Throwable) : DonationError(source, cause) {
    class RequestTokenError(source: DonationErrorSource, cause: Throwable) : GooglePayError(source, cause)
  }

  /**
   * Utilized when the user cancels the payment flow, by either exiting a WebView or not confirming on the complete order sheet.
   */
  class UserCancelledPaymentError(source: DonationErrorSource) : DonationError(source, Exception("User cancelled payment."))

  /**
   * Utilized when the user launches into an external application while viewing the WebView. This should kick us back to the donations
   * screen and await user processing.
   */
  class UserLaunchedExternalApplication(source: DonationErrorSource) : DonationError(source, Exception("User launched external application."))

  /**
   * Gifting recipient validation errors, which occur before the user could be charged for a gift.
   */
  sealed class GiftRecipientVerificationError(cause: Throwable) : DonationError(DonationErrorSource.GIFT, cause) {
    object SelectedRecipientIsInvalid : GiftRecipientVerificationError(Exception("Selected recipient is invalid."))
  }

  /**
   * One-time donation validation errors, which occur before the user could be charged.
   */
  sealed class OneTimeDonationError(source: DonationErrorSource, message: String) : DonationError(source, Exception(message)) {
    class AmountTooSmallError(source: DonationErrorSource) : OneTimeDonationError(source, "Amount is too small")
    class AmountTooLargeError(source: DonationErrorSource) : OneTimeDonationError(source, "Amount is too large")
    class InvalidCurrencyError(source: DonationErrorSource) : OneTimeDonationError(source, "Currency is not supported")
  }

  /**
   * Stripe setup errors, which occur before the user could be charged. These are either
   * payment processing handed to Stripe from the CC company (in the case of a Boost payment
   * intent confirmation error) or other generic error from Stripe.
   */
  sealed class PaymentSetupError(source: DonationErrorSource, cause: Throwable) : DonationError(source, cause) {
    /**
     * Payment setup failed in some generic fashion.
     */
    class GenericError(source: DonationErrorSource, cause: Throwable) : PaymentSetupError(source, cause)

    /**
     * Payment setup failed in some way, which we are told about by Stripe.
     */
    class StripeCodedError(source: DonationErrorSource, cause: Throwable, val errorCode: String) : PaymentSetupError(source, cause)

    /**
     * Payment failed by the credit card processor, with a specific reason told to us by Stripe.
     */
    class StripeDeclinedError(source: DonationErrorSource, cause: Throwable, val declineCode: StripeDeclineCode, val method: PaymentSourceType.Stripe) : PaymentSetupError(source, cause)

    /**
     * Bank Transfer failed, with a specific reason told to us by Stripe
     */
    class StripeFailureCodeError(source: DonationErrorSource, cause: Throwable, val failureCode: StripeFailureCode, val method: PaymentSourceType.Stripe) : PaymentSetupError(source, cause)

    /**
     * Payment setup failed in some way, which we are told about by PayPal.
     */
    class PayPalCodedError(source: DonationErrorSource, cause: Throwable, val errorCode: Int) : PaymentSetupError(source, cause)

    /**
     * Payment failed by the credit card processor, with a specific reason told to us by PayPal.
     */
    class PayPalDeclinedError(source: DonationErrorSource, cause: Throwable, val code: PayPalDeclineCode.KnownCode) : PaymentSetupError(source, cause)
  }

  /**
   * Errors that can be thrown after we submit a payment to Stripe. It is
   * assumed that at this point, anything we submit *could* happen, so we can no
   * longer safely assume a user has not been charged. Payment errors explicitly
   * originate from Signal service.
   */
  sealed class PaymentProcessingError(source: DonationErrorSource, cause: Throwable) : DonationError(source, cause) {
    class GenericError(source: DonationErrorSource) : DonationError(source, Exception("Generic Payment Error"))
  }

  /**
   * Errors that can occur during the badge redemption process.
   */
  sealed class BadgeRedemptionError(source: DonationErrorSource, cause: Throwable) : DonationError(source, cause) {
    /**
     * Timeout elapsed while the user was waiting for badge redemption to complete for a long-running payment.
     * This is not an indication that redemption failed, just that it could take a few days to process the payment.
     */
    class DonationPending(source: DonationErrorSource, val inAppPayment: InAppPaymentTable.InAppPayment) : BadgeRedemptionError(source, Exception("Long-running donation is still pending."))

    /**
     * Timeout elapsed while the user was waiting for badge redemption to complete. This is not an indication that
     * redemption failed, just that it is taking longer than we can reasonably show a spinner.
     */
    class TimeoutWaitingForTokenError(source: DonationErrorSource) : BadgeRedemptionError(source, Exception("Timed out waiting for badge redemption to complete."))

    /**
     * Verification of request credentials object failed
     */
    class FailedToValidateCredentialError(source: DonationErrorSource) : BadgeRedemptionError(source, Exception("Failed to validate credential from server."))

    /**
     * Some generic error not otherwise accounted for occurred during the redemption process.
     */
    class GenericError(source: DonationErrorSource) : BadgeRedemptionError(source, Exception("Failed to add badge to account."))
  }

  companion object {

    private val TAG = Log.tag(DonationError::class.java)

    private val donationErrorSubjectSourceMap: Map<DonationErrorSource, Subject<DonationError>> = DonationErrorSource.values().associate { source ->
      source to PublishSubject.create()
    }

    @JvmStatic
    fun getErrorsForSource(donationErrorSource: DonationErrorSource): Observable<DonationError> {
      return donationErrorSubjectSourceMap[donationErrorSource]!!
    }

    @JvmStatic
    fun DonationError.toDonationErrorValue(): DonationErrorValue {
      return when (this) {
        is PaymentSetupError.GenericError -> DonationErrorValue(
          type = DonationErrorValue.Type.PAYMENT,
          code = ""
        )
        is PaymentSetupError.StripeCodedError -> DonationErrorValue(
          type = DonationErrorValue.Type.PROCESSOR_CODE,
          code = this.errorCode
        )
        is PaymentSetupError.StripeDeclinedError -> DonationErrorValue(
          type = DonationErrorValue.Type.DECLINE_CODE,
          code = this.declineCode.rawCode
        )
        is PaymentSetupError.StripeFailureCodeError -> DonationErrorValue(
          type = DonationErrorValue.Type.FAILURE_CODE,
          code = this.failureCode.rawCode
        )
        is PaymentSetupError.PayPalCodedError -> DonationErrorValue(
          type = DonationErrorValue.Type.PROCESSOR_CODE,
          code = this.errorCode.toString()
        )
        is PaymentSetupError.PayPalDeclinedError -> DonationErrorValue(
          type = DonationErrorValue.Type.DECLINE_CODE,
          code = this.code.code.toString()
        )
        else -> error("Don't know how to convert error $this")
      }
    }

    @JvmStatic
    @JvmOverloads
    fun routeBackgroundError(
      context: Context,
      error: DonationError,
      suppressNotification: Boolean = true
    ) {
      if (error.source == DonationErrorSource.GIFT_REDEMPTION) {
        routeDonationError(context, error)
        return
      }

      when {
        suppressNotification -> {
          Log.i(TAG, "Suppressing notification for error.", error)
        }
        else -> {
          Log.i(TAG, "Routing background donation error to notification", error)
          DonationErrorNotifications.displayErrorNotification(context, error)
        }
      }
    }

    /**
     * Route a given donation error, which will either pipe it out to an appropriate subject
     * or, if the subject has no observers, post it as a notification.
     */
    private fun routeDonationError(context: Context, error: DonationError) {
      val subject: Subject<DonationError> = donationErrorSubjectSourceMap[error.source]!!
      when {
        subject.hasObservers() -> {
          Log.i(TAG, "Routing donation error to subject ${error.source} dialog", error)
          subject.onNext(error)
        }
        else -> {
          Log.i(TAG, "Routing donation error to subject ${error.source} notification", error)
          DonationErrorNotifications.displayErrorNotification(context, error)
        }
      }
    }

    /**
     * Converts a throwable into a payment setup error. This should only be used when
     * handling errors handed back via the Stripe API or via PayPal, when we know for sure that no
     * charge has occurred.
     */
    @JvmStatic
    fun getPaymentSetupError(source: DonationErrorSource, throwable: Throwable, method: PaymentSourceType): DonationError {
      return when (throwable) {
        is StripeError.PostError.Generic -> {
          val errorCode: String? = throwable.errorCode
          if (errorCode != null) {
            PaymentSetupError.StripeCodedError(source, throwable, errorCode)
          } else {
            PaymentSetupError.GenericError(source, throwable)
          }
        }
        is StripeError.PostError.Declined -> PaymentSetupError.StripeDeclinedError(source, throwable, throwable.declineCode, method as PaymentSourceType.Stripe)
        is StripeError.PostError.Failed -> PaymentSetupError.StripeFailureCodeError(source, throwable, throwable.failureCode, method as PaymentSourceType.Stripe)

        is UserCancelledPaymentError -> {
          return throwable
        }

        else -> {
          PaymentSetupError.GenericError(source, throwable)
        }
      }
    }

    @JvmStatic
    fun genericBadgeRedemptionFailure(source: DonationErrorSource): DonationError = BadgeRedemptionError.GenericError(source)

    @JvmStatic
    fun badgeCredentialVerificationFailure(source: DonationErrorSource): DonationError = BadgeRedemptionError.FailedToValidateCredentialError(source)

    @JvmStatic
    fun genericPaymentFailure(source: DonationErrorSource): DonationError = PaymentProcessingError.GenericError(source)
  }
}
