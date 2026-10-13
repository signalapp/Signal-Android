package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord

object DonationReceiptDetailRepository {
  suspend fun getDonationReceiptRecord(id: Long): InAppPaymentReceiptRecord {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.donationReceipts.getReceipt(id)!!
    }
  }
}
