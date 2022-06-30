package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import org.thoughtcrime.securesms.database.model.DonationReceiptRecord

data class DonationReceiptListPageState(
  val records: List<DonationReceiptRecord> = emptyList(),
  val isLoaded: Boolean = false
)
