/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BackupKeyDisplayViewModel : ViewModel(), BackupKeyCredentialManagerHandler {
  private val _uiState = MutableStateFlow(BackupKeyDisplayUiState())
  val uiState: StateFlow<BackupKeyDisplayUiState> = _uiState.asStateFlow()

  override fun updateBackupKeySaveState(newState: BackupKeySaveState?) {
    _uiState.update { it.copy(keySaveState = newState) }
  }
}

data class BackupKeyDisplayUiState(
  val keySaveState: BackupKeySaveState? = null
)
