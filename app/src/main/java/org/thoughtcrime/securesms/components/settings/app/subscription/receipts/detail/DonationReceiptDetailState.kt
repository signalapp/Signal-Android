package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord

/**
 * [DonationReceiptDetailScreen] state.
 */
data class DonationReceiptDetailState(
  /** The receipt being shown, or null while it is still being read. */
  val inAppPaymentReceiptRecord: InAppPaymentReceiptRecord? = null,
  /** Whether the shareable receipt image is being rendered. */
  val isRenderingReceipt: Boolean = false
) {
  val canShare: Boolean
    get() = inAppPaymentReceiptRecord != null && !isRenderingReceipt
}
