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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.local.ArchiveFileSystem
import org.thoughtcrime.securesms.backup.v2.local.SnapshotFileSystem
import org.thoughtcrime.securesms.keyvalue.SignalStore
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

  fun setSelectedBackupDirectory(context: Context, uri: Uri): Boolean {
    SignalStore.backup.newLocalBackupsDirectory = uri.toString()

    val archiveFileSystem = ArchiveFileSystem.fromUri(context, uri)

    if (archiveFileSystem == null) {
      Log.w(TAG, "Unable to access backup directory: $uri")
      internalState.update { it.copy(selectedBackup = null, selectableBackups = persistentListOf(), dialog = RestoreLocalBackupDialog.FAILED_TO_LOAD_ARCHIVE) }
      return false
    }

    val selectableBackups = archiveFileSystem
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

    internalState.update {
      it.copy(
        selectableBackups = selectableBackups,
        selectedBackup = selectableBackups.firstOrNull()
      )
    }
    return true
  }

  fun displaySkipRestoreWarning() {
    internalState.update { it.copy(dialog = RestoreLocalBackupDialog.SKIP_RESTORE_WARNING) }
  }

  fun clearDialog() {
    internalState.update { it.copy(dialog = null) }
  }
}
