package org.thoughtcrime.securesms.components.settings.app.subscription.errors

import android.content.Context
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.core.util.logging.Log
import org.signal.donations.PaymentSourceType
import org.signal.donations.StripeDeclineCode
import org.signal.donations.StripeError

sealed class DonationError(val source: DonationErrorSource, cause: Throwable) : Exception(cause) {

  /**
   * Google Pay errors, which happen well before a user would ever be charged.
   */
  sealed class GooglePayError(source: DonationErrorSource, cause: Throwable) : DonationError(source, cause) {
    class NotAvailableError(source: DonationErrorSource, cause: Throwable) : GooglePayError(source, cause)
    class RequestTokenError(source: DonationErrorSource, cause: Throwable) : GooglePayError(source, cause)
  }

  /**
   * Gifting recipient validation errors, which occur before the user could be charged for a gift.
   */
  sealed class GiftRecipientVerificationError(cause: Throwable) : DonationError(DonationErrorSource.GIFT, cause) {
    object SelectedRecipientIsInvalid : GiftRecipientVerificationError(Exception("Selected recipient is invalid."))
    object SelectedRecipientDoesNotSupportGifts : GiftRecipientVerificationError(Exception("Selected recipient does not support gifts."))
    class FailedToFetchProfile(cause: Throwable) : GiftRecipientVerificationError(Exception("Failed to fetch recipient profile.", cause))
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

    /**
     * Route a given donation error, which will either pipe it out to an appropriate subject
     * or, if the subject has no observers, post it as a notification.
     */
    @JvmStatic
    fun routeDonationError(context: Context, error: DonationError) {
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

    @JvmStatic
    fun getGooglePayRequestTokenError(source: DonationErrorSource, throwable: Throwable): DonationError {
      return GooglePayError.RequestTokenError(source, throwable)
    }

    /**
     * Converts a throwable into a payment setup error. This should only be used when
     * handling errors handed back via the Stripe API or via PayPal, when we know for sure that no
     * charge has occurred.
     */
    @JvmStatic
    fun getPaymentSetupError(source: DonationErrorSource, throwable: Throwable, method: PaymentSourceType): DonationError {
      return if (throwable is StripeError.PostError) {
        val declineCode: StripeDeclineCode? = throwable.declineCode
        val errorCode: String? = throwable.errorCode

        when {
          declineCode != null && method is PaymentSourceType.Stripe -> PaymentSetupError.StripeDeclinedError(source, throwable, declineCode, method)
          errorCode != null && method is PaymentSourceType.Stripe -> PaymentSetupError.StripeCodedError(source, throwable, errorCode)
          else -> PaymentSetupError.GenericError(source, throwable)
        }
      } else {
        PaymentSetupError.GenericError(source, throwable)
      }
    }

    @JvmStatic
    fun oneTimeDonationAmountTooSmall(source: DonationErrorSource): DonationError = OneTimeDonationError.AmountTooSmallError(source)

    @JvmStatic
    fun oneTimeDonationAmountTooLarge(source: DonationErrorSource): DonationError = OneTimeDonationError.AmountTooLargeError(source)

    @JvmStatic
    fun invalidCurrencyForOneTimeDonation(source: DonationErrorSource): DonationError = OneTimeDonationError.InvalidCurrencyError(source)

    @JvmStatic
    fun timeoutWaitingForToken(source: DonationErrorSource): DonationError = BadgeRedemptionError.TimeoutWaitingForTokenError(source)

    @JvmStatic
    fun genericBadgeRedemptionFailure(source: DonationErrorSource): DonationError = BadgeRedemptionError.GenericError(source)

    @JvmStatic
    fun badgeCredentialVerificationFailure(source: DonationErrorSource): DonationError = BadgeRedemptionError.FailedToValidateCredentialError(source)

    @JvmStatic
    fun genericPaymentFailure(source: DonationErrorSource): DonationError = PaymentProcessingError.GenericError(source)
  }
}
