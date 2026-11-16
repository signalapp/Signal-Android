/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import androidx.annotation.StringRes
import org.signal.core.models.badges.Badge
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord

/**
 * [DonationReceiptListScreen] state.
 */
data class DonationReceiptListState(
  /** Every donation receipt, newest first, or null until they have been read. */
  val receipts: List<InAppPaymentReceiptRecord>? = null,
  /** The badge for each kind of donation. Empty until fetched, which needs a network connection. */
  val badges: List<DonationReceiptBadge> = emptyList()
) {

  /** The receipts [tab] shows, or null until they have been read. */
  fun receiptsFor(tab: Tab): List<InAppPaymentReceiptRecord>? {
    val type = tab.type ?: return receipts
    return receipts?.filter { it.type == type }
  }

  /** The badge to show beside [receipt]: the one for its kind of donation, or for its subscription level. */
  fun badgeFor(receipt: InAppPaymentReceiptRecord): Badge? {
    return when (receipt.type) {
      InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION,
      InAppPaymentReceiptRecord.Type.ONE_TIME_GIFT -> badges.firstOrNull { it.type == receipt.type }?.badge
      else -> badges.firstOrNull { it.level == receipt.subscriptionLevel }?.badge
    }
  }

  override fun toString(): String = "DonationReceiptListState(receipts=${receipts?.size}, badges=${badges.size})"

  /** The tabs the receipts are split across, each showing one kind of donation or all of them. */
  enum class Tab(val type: InAppPaymentReceiptRecord.Type?, @StringRes val title: Int) {
    ALL(null, R.string.DonationReceiptListFragment__all),
    RECURRING(InAppPaymentReceiptRecord.Type.RECURRING_DONATION, R.string.DonationReceiptListFragment__recurring),
    ONE_TIME(InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION, R.string.DonationReceiptListFragment__one_time),
    FOR_A_FRIEND(InAppPaymentReceiptRecord.Type.ONE_TIME_GIFT, R.string.DonationReceiptListFragment__for_a_friend)
  }
}
