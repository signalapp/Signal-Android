/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.backup.v2.BackupV2Event
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsFrequency
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * ViewModel for state management of RemoteBackupsSettingsFragment
 */
class RemoteBackupsSettingsViewModel : ViewModel() {
  private val internalState = mutableStateOf(
    RemoteBackupsSettingsState(
      messageBackupsTier = if (SignalStore.backup().areBackupsEnabled) {
        if (SignalStore.backup().canReadWriteToArchiveCdn) {
          MessageBackupTier.PAID
        } else {
          MessageBackupTier.FREE
        }
      } else {
        null
      },
      lastBackupTimestamp = SignalStore.backup().lastBackupTime
    )
  )

  val state: State<RemoteBackupsSettingsState> = internalState

  fun setCanBackUpUsingCellular(canBackUpUsingCellular: Boolean) {
    // TODO [message-backups] -- Update via repository?
    internalState.value = state.value.copy(canBackUpUsingCellular = canBackUpUsingCellular)
  }

  fun setBackupsFrequency(backupsFrequency: MessageBackupsFrequency) {
    // TODO [message-backups] -- Update via repository?
    internalState.value = state.value.copy(backupsFrequency = backupsFrequency)
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
    internalState.value = state.value.copy(backupProgress = backupEvent, lastBackupTimestamp = SignalStore.backup().lastBackupTime)
  }

  fun onBackupNowClick() {
    if (state.value.backupProgress == null || state.value.backupProgress?.type == BackupV2Event.Type.FINISHED) {
      BackupMessagesJob.enqueue()
    }
  }
}
