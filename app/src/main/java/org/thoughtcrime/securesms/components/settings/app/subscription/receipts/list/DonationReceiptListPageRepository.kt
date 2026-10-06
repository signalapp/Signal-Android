package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord

object DonationReceiptListPageRepository {
  suspend fun getRecords(type: InAppPaymentReceiptRecord.Type?): List<InAppPaymentReceiptRecord> {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.donationReceipts.getReceipts(type)
    }
  }
}
