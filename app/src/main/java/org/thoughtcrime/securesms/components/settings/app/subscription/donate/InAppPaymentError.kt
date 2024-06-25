/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData

/**
 * Wraps an InAppPaymentData.Error in a throwable.
 */
class InAppPaymentError(
  val inAppPaymentDataError: InAppPaymentData.Error
) : Exception() {
  companion object {
    fun fromDonationError(donationError: DonationError): InAppPaymentError? {
      val inAppPaymentDataError: InAppPaymentData.Error? = when (donationError) {
        is DonationError.BadgeRedemptionError.DonationPending -> null
        is DonationError.BadgeRedemptionError.FailedToValidateCredentialError -> InAppPaymentData.Error(type = InAppPaymentData.Error.Type.CREDENTIAL_VALIDATION)
        is DonationError.BadgeRedemptionError.GenericError -> InAppPaymentData.Error(type = InAppPaymentData.Error.Type.REDEMPTION)
        is DonationError.BadgeRedemptionError.TimeoutWaitingForTokenError -> null
        is DonationError.PaymentProcessingError.GenericError -> InAppPaymentData.Error(type = InAppPaymentData.Error.Type.PAYMENT_PROCESSING)
        DonationError.GiftRecipientVerificationError.SelectedRecipientIsInvalid -> InAppPaymentData.Error(type = InAppPaymentData.Error.Type.INVALID_GIFT_RECIPIENT)
        is DonationError.GooglePayError.RequestTokenError -> InAppPaymentData.Error(type = InAppPaymentData.Error.Type.GOOGLE_PAY_REQUEST_TOKEN)
        is DonationError.OneTimeDonationError.AmountTooLargeError -> InAppPaymentData.Error(type = InAppPaymentData.Error.Type.ONE_TIME_AMOUNT_TOO_LARGE)
        is DonationError.OneTimeDonationError.AmountTooSmallError -> InAppPaymentData.Error(type = InAppPaymentData.Error.Type.ONE_TIME_AMOUNT_TOO_SMALL)
        is DonationError.OneTimeDonationError.InvalidCurrencyError -> InAppPaymentData.Error(type = InAppPaymentData.Error.Type.INVALID_CURRENCY)
        is DonationError.PaymentSetupError.GenericError -> InAppPaymentData.Error(type = InAppPaymentData.Error.Type.PAYMENT_SETUP)
        is DonationError.PaymentSetupError.PayPalCodedError -> InAppPaymentData.Error(type = InAppPaymentData.Error.Type.PAYPAL_CODED_ERROR, data_ = donationError.errorCode.toString())
        is DonationError.PaymentSetupError.PayPalDeclinedError -> InAppPaymentData.Error(type = InAppPaymentData.Error.Type.PAYPAL_DECLINED_ERROR, data_ = donationError.code.code.toString())
        is DonationError.PaymentSetupError.StripeCodedError -> InAppPaymentData.Error(type = InAppPaymentData.Error.Type.STRIPE_CODED_ERROR, data_ = donationError.errorCode)
        is DonationError.PaymentSetupError.StripeDeclinedError -> InAppPaymentData.Error(type = InAppPaymentData.Error.Type.STRIPE_DECLINED_ERROR, data_ = donationError.declineCode.rawCode)
        is DonationError.PaymentSetupError.StripeFailureCodeError -> InAppPaymentData.Error(type = InAppPaymentData.Error.Type.STRIPE_FAILURE, data_ = donationError.failureCode.rawCode)
        is DonationError.UserCancelledPaymentError -> null
        is DonationError.UserLaunchedExternalApplication -> null
      }

      return inAppPaymentDataError?.let { InAppPaymentError(it) }
    }
  }
}
