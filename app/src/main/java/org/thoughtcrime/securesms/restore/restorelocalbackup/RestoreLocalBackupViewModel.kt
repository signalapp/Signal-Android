/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.restorelocalbackup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.BackupEvent
import org.thoughtcrime.securesms.restore.RestoreRepository

/**
 * ViewModel for [RestoreLocalBackupFragment]
 */
class RestoreLocalBackupViewModel(fileBackupUri: Uri) : ViewModel() {
  private val store = MutableStateFlow(RestoreLocalBackupState(fileBackupUri))
  val uiState = store.asLiveData()

  val backupReadError = store.map { it.backupFileStateError }.asLiveData()

  val backupComplete = store.map { Pair(it.backupRestoreComplete, it.backupImportResult) }.asLiveData()

  fun prepareRestore(context: Context) {
    val backupFileUri = store.value.uri
    viewModelScope.launch {
      val result: RestoreRepository.BackupInfoResult = RestoreRepository.getLocalBackupFromUri(context, backupFileUri)

      if (result.failure && result.failureCause != null) {
        store.update {
          it.copy(
            backupFileStateError = result.failureCause.state
          )
        }
      } else if (result.backupInfo == null) {
        abort()
        return@launch
      }

      store.update {
        it.copy(
          backupInfo = result.backupInfo
        )
      }
    }
  }

  private fun abort() {
    store.update {
      it.copy(abort = true)
    }
  }

  fun confirmPassphraseAndBeginRestore(context: Context, passphrase: String) {
    store.update {
      it.copy(
        backupPassphrase = passphrase,
        restoreInProgress = true
      )
    }

    val backupFileUri = store.value.backupInfo?.uri
    val backupPassphrase = store.value.backupPassphrase
    if (backupFileUri == null) {
      Log.w(TAG, "Could not begin backup import because backup file URI was null!")
      abort()
      return
    }

    if (backupPassphrase.isEmpty()) {
      Log.w(TAG, "Could not begin backup import because backup passphrase was empty!")
      abort()
      return
    }

    viewModelScope.launch {
      val importResult = RestoreRepository.restoreBackupAsynchronously(context, backupFileUri, backupPassphrase)

      store.update {
        it.copy(
          backupImportResult = if (importResult == RestoreRepository.BackupImportResult.SUCCESS) null else importResult,
          restoreInProgress = false,
          backupRestoreComplete = true,
          backupEstimatedTotalCount = -1L,
          backupProgressCount = -1L,
          backupVerifyingInProgress = false
        )
      }
    }
  }

  fun onBackupProgressUpdate(event: BackupEvent) {
    store.update {
      it.copy(
        backupProgressCount = event.count,
        backupEstimatedTotalCount = event.estimatedTotalCount,
        backupVerifyingInProgress = event.type == BackupEvent.Type.PROGRESS_VERIFYING,
        backupRestoreComplete = event.type == BackupEvent.Type.FINISHED,
        restoreInProgress = event.type != BackupEvent.Type.FINISHED
      )
    }
  }

  fun clearBackupFileStateError() {
    store.update { it.copy(backupFileStateError = null) }
  }

  companion object {
    private val TAG = Log.tag(RestoreLocalBackupViewModel::class.java)
  }
}
