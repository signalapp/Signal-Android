/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts

import androidx.annotation.StringRes
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord

/**
 * Display label for a donation receipt type. Backup receipts are not shown in the donation receipt UI.
 */
@get:StringRes
val InAppPaymentReceiptRecord.Type.labelRes: Int
  get() = when (this) {
    InAppPaymentReceiptRecord.Type.RECURRING_DONATION -> R.string.DonationReceiptListFragment__recurring
    InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION -> R.string.DonationReceiptListFragment__one_time
    InAppPaymentReceiptRecord.Type.ONE_TIME_GIFT -> R.string.DonationReceiptListFragment__donation_for_a_friend
    InAppPaymentReceiptRecord.Type.RECURRING_BACKUP -> error("Not supported.")
  }
