/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.errors

import androidx.annotation.StringRes
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.R

/**
 * Methods to delineate donation vs backup payment error strings.
 *
 * The format here should remain that the last word in the method name is that of where
 * it is being placed in a given error dialog/notification.
 */
object InAppPaymentErrorStrings {
  @StringRes
  fun getGenericErrorProcessingTitle(inAppPaymentType: InAppPaymentType): Int {
    return if (inAppPaymentType == InAppPaymentType.RECURRING_BACKUP) {
      R.string.InAppPaymentErrors__error_processing_payment
    } else {
      R.string.DonationsErrors__error_processing_payment
    }
  }

  @StringRes
  fun getPaymentSetupErrorMessage(inAppPaymentType: InAppPaymentType): Int {
    return if (inAppPaymentType == InAppPaymentType.RECURRING_BACKUP) {
      R.string.InAppPaymentErrors__your_payment_couldnt_be_processed
    } else {
      R.string.DonationsErrors__your_payment
    }
  }

  @StringRes
  fun getStillProcessingErrorMessage(inAppPaymentType: InAppPaymentType): Int {
    return if (inAppPaymentType == InAppPaymentType.RECURRING_BACKUP) {
      R.string.InAppPaymentErrors__your_payment_is_still
    } else {
      R.string.DonationsErrors__your_payment_is_still
    }
  }

  @StringRes
  fun getStripeIssuerNotAvailableErrorMessage(inAppPaymentType: InAppPaymentType): Int {
    return if (inAppPaymentType == InAppPaymentType.RECURRING_BACKUP) {
      R.string.InAppPaymentErrors__StripeDeclineCode__try_completing_the_payment_again
    } else {
      R.string.DeclineCode__try_completing_the_payment_again
    }
  }

  @StringRes
  fun getStripeFailureCodeAuthorizationRevokedErrorMessage(inAppPaymentType: InAppPaymentType): Int {
    return if (inAppPaymentType == InAppPaymentType.RECURRING_BACKUP) {
      R.string.InAppPaymentErrors__StripeFailureCode__this_payment_was_revoked
    } else {
      R.string.StripeFailureCode__this_payment_was_revoked
    }
  }

  @StringRes
  fun getStripeFailureCodeDebitAuthorizationNotMatchErrorMessage(inAppPaymentType: InAppPaymentType): Int {
    return if (inAppPaymentType == InAppPaymentType.RECURRING_BACKUP) {
      R.string.InAppPaymentErrors__StripeFailureCode__an_error_occurred_while_processing_this_payment
    } else {
      R.string.StripeFailureCode__an_error_occurred_while_processing_this_payment
    }
  }
}
