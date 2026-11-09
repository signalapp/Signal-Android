/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.Environment

/**
 * All of the storage and network access behind [BackupsSettingsViewModel].
 */
object BackupsSettingsRepository {

  /** The user's backup state, kept current for as long as [scope] lives. */
  fun observeBackupState(scope: CoroutineScope): StateFlow<BackupState> {
    return BackupStateObserver(scope, useDatabaseFallbackOnNetworkError = true).backupState
  }

  fun lastBackupTime(): Long = SignalStore.backup.lastBackupTime

  fun lastBackupTimeFlow(): Flow<Long> = SignalStore.backup.lastBackupTimeFlow

  fun isPrimaryDevice(): Boolean = SignalStore.account.isPrimaryDevice

  fun isLinkedDevice(): Boolean = SignalStore.account.isLinkedDevice

  /** The staging-only override is offered on a primary device in a staging build. */
  fun canOverrideBackupTier(): Boolean = Environment.IS_STAGING && SignalStore.account.isPrimaryDevice

  fun backupTierInternalOverride(): MessageBackupTier? = SignalStore.backup.backupTierInternalOverride

  /** Linked devices learn about backups from the primary, so ask for the latest backup file time. */
  suspend fun refreshBackupFileTimestamp() {
    withContext(SignalDispatchers.IO) {
      BackupRepository.refreshBackupFileTimestamp()
    }
  }

  /** Forces the backup tier to [tier] (or clears the override), then syncs and refreshes the state everyone observes. */
  suspend fun setBackupTierInternalOverride(tier: MessageBackupTier?, scope: CoroutineScope) {
    SignalStore.backup.backupTierInternalOverride = tier
    SignalStore.backup.deletionState = DeletionState.NONE
    withContext(SignalDispatchers.Default) {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
    BackupStateObserver.notifyBackupStateChanged(scope = scope)
  }
}
