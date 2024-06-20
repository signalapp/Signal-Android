/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.backup.v2.BackupFrequency
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.BackupV2Event
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.MessageBackupListener
import kotlin.time.Duration.Companion.milliseconds

/**
 * ViewModel for state management of RemoteBackupsSettingsFragment
 */
class RemoteBackupsSettingsViewModel : ViewModel() {
  private val internalState = MutableStateFlow(
    RemoteBackupsSettingsState(
      messageBackupsType = null,
      lastBackupTimestamp = SignalStore.backup.lastBackupTime,
      backupSize = SignalStore.backup.totalBackupSize,
      backupsFrequency = SignalStore.backup.backupFrequency
    )
  )

  val state: StateFlow<RemoteBackupsSettingsState> = internalState

  init {
    refresh()
  }

  fun setCanBackUpUsingCellular(canBackUpUsingCellular: Boolean) {
    SignalStore.backup.backupWithCellular = canBackUpUsingCellular
    internalState.update { it.copy(canBackUpUsingCellular = canBackUpUsingCellular) }
  }

  fun setBackupsFrequency(backupsFrequency: BackupFrequency) {
    SignalStore.backup.backupFrequency = backupsFrequency
    internalState.update { it.copy(backupsFrequency = backupsFrequency) }
    MessageBackupListener.setNextBackupTimeToIntervalFromNow()
    MessageBackupListener.schedule(AppDependencies.application)
  }

  fun requestDialog(dialog: RemoteBackupsSettingsState.Dialog) {
    internalState.update { it.copy(dialog = dialog) }
  }

  fun requestSnackbar(snackbar: RemoteBackupsSettingsState.Snackbar) {
    internalState.update { it.copy(snackbar = snackbar) }
  }

  fun refresh() {
    viewModelScope.launch {
      val tier = SignalStore.backup.backupTier
      val backupType = if (tier != null) BackupRepository.getBackupsType(tier) else null

      internalState.update {
        it.copy(
          messageBackupsType = backupType,
          lastBackupTimestamp = SignalStore.backup.lastBackupTime,
          backupSize = SignalStore.backup.totalBackupSize,
          backupsFrequency = SignalStore.backup.backupFrequency
        )
      }
    }
  }

  fun turnOffAndDeleteBackups() {
    viewModelScope.launch {
      requestDialog(RemoteBackupsSettingsState.Dialog.DELETING_BACKUP)

      withContext(Dispatchers.IO) {
        BackupRepository.turnOffAndDeleteBackup()
      }

      if (isActive) {
        requestDialog(RemoteBackupsSettingsState.Dialog.BACKUP_DELETED)
        delay(2000.milliseconds)
        requestDialog(RemoteBackupsSettingsState.Dialog.NONE)
      }
    }
  }

  fun updateBackupProgress(backupEvent: BackupV2Event?) {
    internalState.update { it.copy(backupProgress = backupEvent) }
    refreshBackupState()
  }

  private fun refreshBackupState() {
    internalState.update {
      it.copy(
        lastBackupTimestamp = SignalStore.backup.lastBackupTime,
        backupSize = SignalStore.backup.totalBackupSize
      )
    }
  }

  fun onBackupNowClick() {
    if (internalState.value.backupProgress == null || internalState.value.backupProgress?.type == BackupV2Event.Type.FINISHED) {
      BackupMessagesJob.enqueue()
    }
  }
}
