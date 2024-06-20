/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups.type

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.keyvalue.SignalStore

class BackupsTypeSettingsViewModel : ViewModel() {
  private val internalState = mutableStateOf(BackupsTypeSettingsState())

  val state: State<BackupsTypeSettingsState> = internalState

  init {
    refresh()
  }

  fun refresh() {
    viewModelScope.launch {
      val tier = SignalStore.backup.backupTier
      internalState.value = state.value.copy(
        messageBackupsType = if (tier != null) BackupRepository.getBackupsType(tier) else null
      )
    }
  }
}
