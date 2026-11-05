package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import android.net.Uri
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.components.settings.app.subscription.receipts.ReceiptImageRenderer
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies

object DonationReceiptDetailRepository {
  suspend fun getDonationReceiptRecord(id: Long): InAppPaymentReceiptRecord {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.donationReceipts.getReceipt(id)!!
    }
  }

  suspend fun renderReceiptImage(record: InAppPaymentReceiptRecord): Uri {
    return ReceiptImageRenderer.renderPng(AppDependencies.application, record)
  }
}
