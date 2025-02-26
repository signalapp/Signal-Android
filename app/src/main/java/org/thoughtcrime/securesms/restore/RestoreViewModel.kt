/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.Skipped
import org.thoughtcrime.securesms.keyvalue.skippedRestoreChoice
import org.thoughtcrime.securesms.registrationv3.data.QuickRegistrationRepository
import org.thoughtcrime.securesms.registrationv3.ui.restore.RestoreMethod
import org.thoughtcrime.securesms.registrationv3.ui.restore.StorageServiceRestore
import org.thoughtcrime.securesms.restore.transferorrestore.BackupRestorationType
import org.whispersystems.signalservice.api.registration.RestoreMethod as ApiRestoreMethod

/**
 * Shared view model for the restore flow.
 */
class RestoreViewModel : ViewModel() {
  private val store = MutableStateFlow(RestoreState())
  val uiState = store.asLiveData()

  var showStorageAccountRestoreProgress by mutableStateOf(false)
    private set

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

  fun hasMultipleRestoreMethods(): Boolean {
    return getAvailableRestoreMethods().size > 1
  }

  fun getAvailableRestoreMethods(): List<RestoreMethod> {
    if (SignalStore.registration.isOtherDeviceAndroid || SignalStore.registration.restoreDecisionState.skippedRestoreChoice) {
      val methods = mutableListOf(RestoreMethod.FROM_OLD_DEVICE, RestoreMethod.FROM_LOCAL_BACKUP_V1)
      when (SignalStore.backup.backupTier) {
        MessageBackupTier.FREE -> methods.add(1, RestoreMethod.FROM_SIGNAL_BACKUPS)
        MessageBackupTier.PAID -> methods.add(0, RestoreMethod.FROM_SIGNAL_BACKUPS)
        null -> if (!SignalStore.backup.isBackupTierRestored) {
          methods.add(1, RestoreMethod.FROM_SIGNAL_BACKUPS)
        }
      }

      return methods
    }

    if (SignalStore.backup.backupTier != null || !SignalStore.backup.isBackupTierRestored) {
      return listOf(RestoreMethod.FROM_SIGNAL_BACKUPS)
    }

    return emptyList()
  }

  fun hasRestoredAccountEntropyPool(): Boolean {
    return SignalStore.account.restoredAccountEntropyPool
  }

  fun skipRestore() {
    SignalStore.registration.restoreDecisionState = RestoreDecisionState.Skipped

    viewModelScope.launch {
      QuickRegistrationRepository.setRestoreMethodForOldDevice(ApiRestoreMethod.DECLINE)
    }
  }

  suspend fun performStorageServiceAccountRestoreIfNeeded() {
    if (hasRestoredAccountEntropyPool() || SignalStore.svr.masterKeyForInitialDataRestore != null) {
      showStorageAccountRestoreProgress = true
      StorageServiceRestore.restore()
    }
  }
}
