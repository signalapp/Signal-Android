package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord

data class DonationReceiptListPageState(
  val records: List<InAppPaymentReceiptRecord> = emptyList(),
  val isLoaded: Boolean = false
)
