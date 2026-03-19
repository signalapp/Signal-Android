/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.local.ArchiveFileSystem
import org.thoughtcrime.securesms.backup.v2.local.LocalArchiver
import org.thoughtcrime.securesms.backup.v2.local.SnapshotFileSystem
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import java.util.concurrent.atomic.AtomicInteger

class EnterBackupKeyViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(EnterBackupKeyViewModel::class)
  }

  private val store = MutableStateFlow(
    EnterBackupKeyState(
      requiredLength = 64,
      chunkLength = 4
    )
  )

  private val verifyGeneration = AtomicInteger(0)

  var backupKey by mutableStateOf("")
    private set

  val state: StateFlow<EnterBackupKeyState> = store

  fun updateBackupKey(key: String) {
    val newKey = AccountEntropyPool.removeIllegalCharacters(key).take(AccountEntropyPool.LENGTH + 16).lowercase()
    val changed = newKey != backupKey
    backupKey = newKey
    store.update {
      val (isValid, updatedError) = AccountEntropyPoolVerification.verifyAEP(
        backupKey = backupKey,
        changed = changed,
        previousAEPValidationError = it.aepValidationError
      )
      it.copy(backupKeyValid = isValid, aepValidationError = updatedError)
    }
  }

  fun verifyLocalBackupKey(selectedTimestamp: Long) {
    if (!state.value.backupKeyValid) {
      return
    }

    val generation = verifyGeneration.incrementAndGet()
    store.update { it.copy(backupKeyValid = false) }

    viewModelScope.launch(Dispatchers.IO) {
      val result = verifyKey(selectedTimestamp)

      if (verifyGeneration.get() == generation) {
        if (result) {
          store.update { it.copy(backupKeyValid = true) }
        } else {
          store.update { it.copy(aepValidationError = AccountEntropyPoolVerification.AEPValidationError.Incorrect) }
        }
      }
    }
  }

  private fun verifyKey(selectedTimestamp: Long): Boolean {
    try {
      val aep = AccountEntropyPool.parseOrNull(backupKey) ?: return false

      val dirUri = SignalStore.backup.newLocalBackupsDirectory ?: return false
      val archiveFileSystem = ArchiveFileSystem.fromUri(AppDependencies.application, Uri.parse(dirUri)) ?: return false
      val snapshot = archiveFileSystem.listSnapshots().firstOrNull { it.timestamp == selectedTimestamp } ?: return false

      val snapshotFs = SnapshotFileSystem(AppDependencies.application, snapshot.file)
      return LocalArchiver.canDecryptMainArchive(snapshotFs, aep.deriveMessageBackupKey())
    } catch (e: Exception) {
      Log.w(TAG, "Failed to verify local backup key", e)
      return false
    }
  }

  fun registering() {
    store.update { it.copy(isRegistering = true) }
  }

  /** Resets [EnterBackupKeyState.isRegistering] without triggering a registration error dialog. Use when navigation away from this screen is handling the error itself. */
  fun cancelRegistering() {
    store.update { it.copy(isRegistering = false) }
  }

  fun handleRegistrationFailure(registerAccountResult: RegisterAccountResult) {
    store.update {
      if (it.isRegistering) {
        Log.w(TAG, "Unable to register [${registerAccountResult::class.simpleName}]", registerAccountResult.getCause(), true)
        val incorrectKeyError = registerAccountResult is RegisterAccountResult.IncorrectRecoveryPassword

        if (incorrectKeyError && SignalStore.account.restoredAccountEntropyPool) {
          SignalStore.account.resetAccountEntropyPool()
          SignalStore.account.resetAciAndPniIdentityKeysAfterFailedRestore()
        }

        it.copy(
          isRegistering = false,
          showRegistrationError = true,
          registerAccountResult = registerAccountResult,
          aepValidationError = if (incorrectKeyError) AccountEntropyPoolVerification.AEPValidationError.Incorrect else it.aepValidationError
        )
      } else {
        it
      }
    }
  }

  fun clearRegistrationError() {
    store.update {
      it.copy(
        showRegistrationError = false,
        registerAccountResult = null
      )
    }
  }

  fun handleBackupTimestampNotRestored() {
    store.update {
      it.copy(
        showBackupTierNotRestoreError = if (SignalStore.backup.isBackupTimestampRestored) TierRestoreError.NOT_FOUND else TierRestoreError.NETWORK_ERROR
      )
    }
  }

  fun hideRestoreBackupKeyFailed() {
    store.update {
      it.copy(
        showBackupTierNotRestoreError = null
      )
    }
  }

  fun incrementBackupTierRetry() {
    store.update { it.copy(tierRetryAttempts = it.tierRetryAttempts + 1) }
  }

  data class EnterBackupKeyState(
    val backupKeyValid: Boolean = false,
    val requiredLength: Int,
    val chunkLength: Int,
    val isRegistering: Boolean = false,
    val showRegistrationError: Boolean = false,
    val showBackupTierNotRestoreError: TierRestoreError? = null,
    val registerAccountResult: RegisterAccountResult? = null,
    val aepValidationError: AccountEntropyPoolVerification.AEPValidationError? = null,
    val tierRetryAttempts: Int = 0
  )

  enum class TierRestoreError {
    NOT_FOUND,
    NETWORK_ERROR
  }
}
