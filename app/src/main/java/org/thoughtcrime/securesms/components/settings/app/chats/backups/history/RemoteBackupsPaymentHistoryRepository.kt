/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups.history

import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord

object RemoteBackupsPaymentHistoryRepository {

  fun getReceipts(): List<InAppPaymentReceiptRecord> {
    return SignalDatabase.donationReceipts.getReceipts(InAppPaymentReceiptRecord.Type.RECURRING_BACKUP)
  }
}
