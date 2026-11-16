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
import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.StagedBackupKeyRotations
import org.thoughtcrime.securesms.keyvalue.SignalStore

class BackupKeyDisplayViewModel : ViewModel(), BackupKeyCredentialManagerHandler {

  companion object {
    private val TAG = Log.tag(BackupKeyDisplayViewModel::class)
  }

  private val internalUiState = MutableStateFlow(BackupKeyDisplayUiState())
  val uiState: StateFlow<BackupKeyDisplayUiState> = internalUiState.asStateFlow()

  override fun updateBackupKeySaveState(newState: BackupKeySaveState?) {
    internalUiState.update { it.copy(keySaveState = newState) }
  }

  init {
    getKeyRotationLimit()
  }

  /**
   * Generates a replacement AEP, provided the user is still allowed to. Callers can be several screens removed from the
   * checks that gate this, so everything is re-verified here rather than trusted.
   */
  fun rotateBackupKey() {
    viewModelScope.launch {
      if (internalUiState.value.rotationState != BackupKeyRotationState.NOT_STARTED) {
        Log.w(TAG, "Rotation already underway. Ignoring.")
        return@launch
      }

      val canRotateKey = BackupRepository.canRotateBackupKey()
      val isOptimizedStorageEnabled = SignalStore.backup.optimizeStorage

      if (!canRotateKey || isOptimizedStorageEnabled) {
        Log.w(TAG, "Refusing to rotate the backup key. canRotateKey: $canRotateKey, isOptimizedStorageEnabled: $isOptimizedStorageEnabled")
        internalUiState.update {
          it.copy(
            canRotateKey = canRotateKey,
            isOptimizedStorageEnabled = isOptimizedStorageEnabled,
            rotationState = BackupKeyRotationState.NOT_ALLOWED
          )
        }
        return@launch
      }

      internalUiState.update { it.copy(rotationState = BackupKeyRotationState.GENERATING_KEY) }

      val stagedKeyRotations = withContext(SignalDispatchers.Default) {
        BackupRepository.stageBackupKeyRotations()
      }

      internalUiState.update {
        it.copy(
          accountEntropyPool = stagedKeyRotations.aep,
          stagedKeyRotations = stagedKeyRotations,
          rotationState = BackupKeyRotationState.USER_VERIFICATION
        )
      }
    }
  }

  fun commitBackupKey() {
    viewModelScope.launch {
      internalUiState.update { it.copy(rotationState = BackupKeyRotationState.COMMITTING_KEY) }

      val keyRotations = internalUiState.value.stagedKeyRotations ?: error("No key rotations to commit!")

      withContext(SignalDispatchers.IO) {
        BackupRepository.commitAEPKeyRotation(keyRotations)
      }

      internalUiState.update { it.copy(rotationState = BackupKeyRotationState.FINISHED) }
    }
  }

  fun getKeyRotationLimit() {
    viewModelScope.launch {
      val canRotateKey = BackupRepository.canRotateBackupKey()
      internalUiState.update { it.copy(canRotateKey = canRotateKey) }
    }
  }

  /** The user dismissed the dialog explaining why we refused to rotate their key. */
  fun onRotationRefusalAcknowledged() {
    internalUiState.update { it.copy(rotationState = BackupKeyRotationState.NOT_STARTED) }
  }

  fun turnOffOptimizedStorageAndDownloadMedia() {
    // TODO - flag to notify when complete.
    BackupRepository.turnOffOptimizedStorageAndDownloadMedia()
  }
}

data class BackupKeyDisplayUiState(
  val accountEntropyPool: AccountEntropyPool = SignalStore.account.accountEntropyPool,
  val keySaveState: BackupKeySaveState? = null,
  val isOptimizedStorageEnabled: Boolean = SignalStore.backup.optimizeStorage,
  val rotationState: BackupKeyRotationState = BackupKeyRotationState.NOT_STARTED,
  val stagedKeyRotations: StagedBackupKeyRotations? = null,
  val canRotateKey: Boolean = true,
  val areBackupsEnabled: Boolean = SignalStore.backup.areBackupsEnabled
)

enum class BackupKeyRotationState {
  NOT_STARTED,

  /** We refused to start a rotation because the user is out of permits or still has storage optimization on. */
  NOT_ALLOWED,
  GENERATING_KEY,
  USER_VERIFICATION,
  COMMITTING_KEY,
  FINISHED
}
