package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord

data class DonationReceiptDetailState(
  val inAppPaymentReceiptRecord: InAppPaymentReceiptRecord? = null,
  val subscriptionName: String? = null
)
