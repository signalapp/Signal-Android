/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.enterbackupkey

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
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registrationv3.ui.restore.AccountEntropyPoolVerification
import org.thoughtcrime.securesms.registrationv3.ui.restore.AccountEntropyPoolVerification.AEPValidationError
import org.whispersystems.signalservice.api.AccountEntropyPool

class PostRegistrationEnterBackupKeyViewModel : ViewModel() {
  companion object {
    private val TAG = Log.tag(PostRegistrationEnterBackupKeyViewModel::class)
  }

  private val store = MutableStateFlow(
    PostRegistrationEnterBackupKeyState()
  )

  var backupKey by mutableStateOf("")
    private set

  val state: StateFlow<PostRegistrationEnterBackupKeyState> = store

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

  fun restoreBackupTier() {
    store.update { it.copy(inProgress = true) }
    viewModelScope.launch(Dispatchers.IO) {
      val aep = AccountEntropyPool.parseOrNull(backupKey)
      val backupTier = withContext(Dispatchers.IO) {
        if (aep != null) {
          BackupRepository.verifyBackupKeyAssociatedWithAccount(SignalStore.account.requireAci(), aep)
        } else {
          Log.w(TAG, "Parsed AEP is null, failing")
          null
        }
      }

      if (backupTier != null) {
        Log.i(TAG, "Backup tier found with entered AEP, migrating to new AEP and moving on to restore")
        SignalStore.account.restoreAccountEntropyPool(aep!!)
        store.update { it.copy(restoreBackupTierSuccessful = true) }
      } else {
        Log.w(TAG, "Unable to validate AEP against currently registered account")
        store.update { it.copy(showBackupTierNotRestoreError = true) }
      }
    }
  }

  fun hideRestoreBackupTierFailed() {
    store.update {
      it.copy(showBackupTierNotRestoreError = false, inProgress = false)
    }
  }

  data class PostRegistrationEnterBackupKeyState(
    val backupKeyValid: Boolean = false,
    val inProgress: Boolean = false,
    val restoreBackupTierSuccessful: Boolean = false,
    val showBackupTierNotRestoreError: Boolean = false,
    val aepValidationError: AEPValidationError? = null
  )
}
