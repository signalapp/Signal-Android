/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.thoughtcrime.securesms.devicetransfer.newdevice.BackupRestorationType

/**
 * Shared view model for the restore flow.
 */
class RestoreViewModel : ViewModel() {
  private val store = MutableStateFlow(RestoreState())
  val uiState = store.asLiveData()

  fun setNextIntent(nextIntent: Intent) {
    store.update {
      it.copy(nextIntent = nextIntent)
    }
  }

  fun onTransferFromAndroidDeviceSelected() {
    store.update {
      it.copy(restorationType = BackupRestorationType.DEVICE_TRANSFER)
    }
  }

  fun onRestoreFromLocalBackupSelected() {
    store.update {
      it.copy(restorationType = BackupRestorationType.LOCAL_BACKUP)
    }
  }

  fun onRestoreFromRemoteBackupSelected() {
    store.update {
      it.copy(restorationType = BackupRestorationType.REMOTE_BACKUP)
    }
  }

  fun getBackupRestorationType(): BackupRestorationType {
    return store.value.restorationType
  }

  fun setBackupFileUri(backupFileUri: Uri) {
    store.update {
      it.copy(backupFile = backupFileUri)
    }
  }

  fun getBackupFileUri(): Uri? = store.value.backupFile

  fun getNextIntent(): Intent? = store.value.nextIntent
}
