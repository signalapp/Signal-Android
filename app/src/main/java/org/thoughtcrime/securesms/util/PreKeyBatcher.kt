/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper.scheduleSyncForDataChange
import org.whispersystems.signalservice.api.keys.PreKeyRepository

/**
 * Helper to batch recipient updates and storage sync when doing a large prekey fetch.
 *
 * See [PreKeyRepository.BatchHelper] for additional details.
 */
object PreKeyBatcher : PreKeyRepository.BatchHelper {

  override fun batch(block: Runnable) {
    val affected: MutableSet<RecipientId> = HashSet()

    try {
      IdentityTable.SUPPRESS_RECIPIENT_REFRESH.set(affected)
      block.run()
      if (!affected.isEmpty()) {
        SignalDatabase.recipients.markNeedsSyncWithoutRefresh(affected)
      }
    } finally {
      IdentityTable.SUPPRESS_RECIPIENT_REFRESH.remove()
    }

    if (!affected.isEmpty()) {
      AppDependencies.recipientCache.refresh(affected)
      scheduleSyncForDataChange()
    }
  }
}
