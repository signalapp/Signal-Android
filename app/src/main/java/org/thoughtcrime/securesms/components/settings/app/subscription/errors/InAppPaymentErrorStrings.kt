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
}
