/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsFrequency

/**
 * ViewModel for state management of RemoteBackupsSettingsFragment
 */
class RemoteBackupsSettingsViewModel : ViewModel() {
  private val internalState = mutableStateOf(RemoteBackupsSettingsState())

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
    internalState.value = state.value.copy(snackbar = RemoteBackupsSettingsState.Snackbar.BACKUP_DELETED_AND_TURNED_OFF)
  }
}
