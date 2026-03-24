/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.local.ArchiveFileSystem
import org.thoughtcrime.securesms.backup.v2.local.LocalArchiver
import org.thoughtcrime.securesms.backup.v2.local.SnapshotFileSystem
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.ui.restore.StorageServiceRestore
import org.thoughtcrime.securesms.util.DateUtils
import java.util.Locale

class RestoreLocalBackupViewModel : ViewModel() {
  companion object {
    private val TAG = Log.tag(RestoreLocalBackupViewModel::class.java)
  }

  private val internalState = MutableStateFlow(RestoreLocalBackupState())

  val state: StateFlow<RestoreLocalBackupState> = internalState

  fun setSelectedBackup(backup: SelectableBackup) {
    internalState.update { it.copy(selectedBackup = backup) }
  }

  suspend fun setSelectedBackupDirectory(context: Context, uri: Uri): Boolean {
    SignalStore.backup.newLocalBackupsDirectory = uri.toString()
    internalState.update { it.copy(isLoadingBackupDirectory = true) }

    val archiveFileSystem = withContext(Dispatchers.IO) { ArchiveFileSystem.openForRestore(context, uri) }

    if (archiveFileSystem == null) {
      Log.w(TAG, "Unable to access backup directory: $uri")
      internalState.update { it.copy(isLoadingBackupDirectory = false, selectedBackup = null, selectableBackups = persistentListOf(), dialog = RestoreLocalBackupDialog.FAILED_TO_LOAD_ARCHIVE) }
      return false
    }

    val selectableBackups = withContext(Dispatchers.IO) {
      archiveFileSystem
        .listSnapshots()
        .take(2)
        .map { snapshot ->
          val dateLabel = if (DateUtils.isSameDay(System.currentTimeMillis(), snapshot.timestamp)) {
            context.getString(R.string.DateUtils_today)
          } else {
            DateUtils.formatDateWithYear(Locale.getDefault(), snapshot.timestamp)
          }
          val timeLabel = DateUtils.getOnlyTimeString(context, snapshot.timestamp)
          val sizeBytes = SnapshotFileSystem(context, snapshot.file).mainLength() ?: 0L

          SelectableBackup(
            timestamp = snapshot.timestamp,
            backupTime = "$dateLabel • $timeLabel",
            backupSize = sizeBytes.bytes.toUnitString()
          )
        }
        .toPersistentList()
    }

    if (selectableBackups.isEmpty()) {
      Log.w(TAG, "No snapshots found in backup directory: $uri")
      internalState.update { it.copy(isLoadingBackupDirectory = false, selectedBackup = null, selectableBackups = persistentListOf(), dialog = RestoreLocalBackupDialog.FAILED_TO_LOAD_ARCHIVE) }
      return false
    }

    internalState.update {
      it.copy(
        isLoadingBackupDirectory = false,
        selectableBackups = selectableBackups,
        selectedBackup = selectableBackups.first()
      )
    }
    return true
  }

  fun displaySkipRestoreWarning() {
    internalState.update { it.copy(dialog = RestoreLocalBackupDialog.SKIP_RESTORE_WARNING) }
  }

  fun displayDifferentAccountWarning() {
    internalState.update { it.copy(dialog = RestoreLocalBackupDialog.CONFIRM_DIFFERENT_ACCOUNT) }
  }

  /** Returns true if the backup at [timestamp] was created by the currently registered account, false if it belongs to a different account. */
  suspend fun backupBelongsToCurrentAccount(context: Context, backupKey: String, timestamp: Long): Boolean {
    return withContext(Dispatchers.IO) {
      val aep = requireNotNull(AccountEntropyPool.parseOrNull(backupKey)) { "Backup key must be valid at submission time" }
      val messageBackupKey = aep.deriveMessageBackupKey()
      val dirUri = requireNotNull(SignalStore.backup.newLocalBackupsDirectory) { "Backup directory must be set" }
      val archiveFileSystem = requireNotNull(ArchiveFileSystem.openForRestore(context, Uri.parse(dirUri))) { "Backup directory must be accessible" }
      val snapshot = requireNotNull(archiveFileSystem.listSnapshots().firstOrNull { it.timestamp == timestamp }) { "Selected snapshot must still exist" }
      val snapshotFs = SnapshotFileSystem(context, snapshot.file)
      val actualBackupId = LocalArchiver.getBackupId(snapshotFs, messageBackupKey)
      if (actualBackupId == null) {
        Log.w(TAG, "backupBelongsToCurrentAccount: getBackupId returned null, treating as current account")
        return@withContext true
      }
      val expectedBackupId = messageBackupKey.deriveBackupId(SignalStore.account.requireAci())
      val matches = actualBackupId.value.contentEquals(expectedBackupId.value)
      Log.d(TAG, "backupBelongsToCurrentAccount: matches=$matches")
      matches
    }
  }

  suspend fun performStorageServiceAccountRestoreIfNeeded() {
    if (SignalStore.account.restoredAccountEntropyPool || SignalStore.svr.masterKeyForInitialDataRestore != null) {
      StorageServiceRestore.restore()
    }
  }

  fun clearDialog() {
    internalState.update { it.copy(dialog = null) }
  }
}
