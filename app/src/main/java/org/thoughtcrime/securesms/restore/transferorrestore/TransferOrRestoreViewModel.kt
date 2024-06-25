/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.transferorrestore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.thoughtcrime.securesms.devicetransfer.newdevice.BackupRestorationType

class TransferOrRestoreViewModel : ViewModel() {

  private val store = MutableStateFlow(State())
  val uiState = store.asLiveData()

  fun onSkipRestoreOrTransferSelected() {
    store.update {
      it.copy(restorationType = BackupRestorationType.NONE)
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

  fun getBackupRestorationType(): BackupRestorationType? {
    return store.value.restorationType
  }
}

data class State(val restorationType: BackupRestorationType? = null)
