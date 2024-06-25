/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups.history

import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DonationReceiptRecord

object RemoteBackupsPaymentHistoryRepository {

  fun getReceipts(): List<DonationReceiptRecord> {
    return SignalDatabase.donationReceipts.getReceipts(DonationReceiptRecord.Type.RECURRING_BACKUP)
  }
}
