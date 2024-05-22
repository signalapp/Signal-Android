/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.backup.v2.BackupFrequency
import org.thoughtcrime.securesms.backup.v2.BackupV2Event
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.MessageBackupListener

/**
 * ViewModel for state management of RemoteBackupsSettingsFragment
 */
class RemoteBackupsSettingsViewModel : ViewModel() {
  private val internalState = mutableStateOf(
    RemoteBackupsSettingsState(
      messageBackupsTier = SignalStore.backup().backupTier,
      lastBackupTimestamp = SignalStore.backup().lastBackupTime,
      backupSize = SignalStore.backup().totalBackupSize,
      backupsFrequency = SignalStore.backup().backupFrequency
    )
  )

  val state: State<RemoteBackupsSettingsState> = internalState

  fun setCanBackUpUsingCellular(canBackUpUsingCellular: Boolean) {
    SignalStore.backup().backupWithCellular = canBackUpUsingCellular
    internalState.value = state.value.copy(canBackUpUsingCellular = canBackUpUsingCellular)
  }

  fun setBackupsFrequency(backupsFrequency: BackupFrequency) {
    SignalStore.backup().backupFrequency = backupsFrequency
    internalState.value = state.value.copy(backupsFrequency = backupsFrequency)
    MessageBackupListener.setNextBackupTimeToIntervalFromNow()
    MessageBackupListener.schedule(AppDependencies.application)
  }

  fun requestDialog(dialog: RemoteBackupsSettingsState.Dialog) {
    internalState.value = state.value.copy(dialog = dialog)
  }

  fun requestSnackbar(snackbar: RemoteBackupsSettingsState.Snackbar) {
    internalState.value = state.value.copy(snackbar = snackbar)
  }

  fun turnOffAndDeleteBackups() {
    // TODO [message-backups] -- Delete.
    SignalStore.backup().areBackupsEnabled = false
    internalState.value = state.value.copy(snackbar = RemoteBackupsSettingsState.Snackbar.BACKUP_DELETED_AND_TURNED_OFF)
  }

  fun updateBackupProgress(backupEvent: BackupV2Event?) {
    internalState.value = state.value.copy(backupProgress = backupEvent)
    refreshBackupState()
  }

  private fun refreshBackupState() {
    internalState.value = state.value.copy(
      lastBackupTimestamp = SignalStore.backup().lastBackupTime,
      backupSize = SignalStore.backup().totalBackupSize
    )
  }

  fun onBackupNowClick() {
    if (state.value.backupProgress == null || state.value.backupProgress?.type == BackupV2Event.Type.FINISHED) {
      BackupMessagesJob.enqueue()
    }
  }
}
