/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.RestoreOptimizedMediaJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.AccountEntropyPool

class BackupKeyDisplayViewModel : ViewModel(), BackupKeyCredentialManagerHandler {
  private val _uiState = MutableStateFlow(BackupKeyDisplayUiState())
  val uiState: StateFlow<BackupKeyDisplayUiState> = _uiState.asStateFlow()

  override fun updateBackupKeySaveState(newState: BackupKeySaveState?) {
    _uiState.update { it.copy(keySaveState = newState) }
  }

  fun rotateBackupKey() {
    viewModelScope.launch {
      _uiState.update { it.copy(rotationState = BackupKeyRotationState.GENERATING_KEY) }

      val stagedAEP = withContext(SignalDispatchers.IO) {
        BackupRepository.stageAEPKeyRotation()
      }

      _uiState.update {
        it.copy(
          accountEntropyPool = stagedAEP,
          rotationState = BackupKeyRotationState.USER_VERIFICATION
        )
      }
    }
  }

  fun commitBackupKey() {
    viewModelScope.launch {
      _uiState.update { it.copy(rotationState = BackupKeyRotationState.COMMITTING_KEY) }

      withContext(SignalDispatchers.IO) {
        BackupRepository.commitAEPKeyRotation(_uiState.value.accountEntropyPool)
      }

      _uiState.update { it.copy(rotationState = BackupKeyRotationState.FINISHED) }
    }
  }

  fun turnOffOptimizedStorageAndDownloadMedia() {
    SignalStore.backup.optimizeStorage = false
    // TODO - flag to notify when complete.
    AppDependencies.jobManager.add(RestoreOptimizedMediaJob())
  }
}

data class BackupKeyDisplayUiState(
  val accountEntropyPool: AccountEntropyPool = SignalStore.account.accountEntropyPool,
  val keySaveState: BackupKeySaveState? = null,
  val isOptimizedStorageEnabled: Boolean = SignalStore.backup.optimizeStorage,
  val rotationState: BackupKeyRotationState = BackupKeyRotationState.NOT_STARTED
)

enum class BackupKeyRotationState {
  NOT_STARTED,
  GENERATING_KEY,
  USER_VERIFICATION,
  COMMITTING_KEY,
  FINISHED
}
