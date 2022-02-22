package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import org.thoughtcrime.securesms.database.model.DonationReceiptRecord

data class DonationReceiptDetailState(
  val donationReceiptRecord: DonationReceiptRecord? = null,
  val subscriptionName: String? = null
)
