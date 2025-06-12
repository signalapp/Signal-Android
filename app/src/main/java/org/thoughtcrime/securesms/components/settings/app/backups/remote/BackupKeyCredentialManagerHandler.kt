/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import org.signal.core.util.logging.Log
import org.signal.core.util.logging.logW

/**
 * Handles the process of storing a backup key to the device password manager.
 */
interface BackupKeyCredentialManagerHandler {
  companion object {
    private val TAG = Log.tag(BackupKeyCredentialManagerHandler::class)
  }

  /** Updates the [BackupKeySaveState]. Implementers must update their associated state to match [newState]. */
  fun updateBackupKeySaveState(newState: BackupKeySaveState?)

  /** Called when the user initiates the backup key save flow. */
  fun onBackupKeySaveRequested() = updateBackupKeySaveState(BackupKeySaveState.RequestingConfirmation)

  /** Called when the user confirms they want to save the backup key to the password manager. */
  fun onBackupKeySaveConfirmed() = updateBackupKeySaveState(BackupKeySaveState.AwaitingCredentialManager(isRetry = false))

  /** Handles the password manager save operation response. */
  fun onBackupKeySaveCompleted(result: CredentialManagerResult) {
    when (result) {
      is CredentialManagerResult.Success -> {
        Log.d(TAG, "Successfully saved backup key to credential manager.")
        updateBackupKeySaveState(newState = BackupKeySaveState.Success)
      }

      is CredentialManagerResult.UserCanceled -> {
        Log.d(TAG, "User canceled saving backup key to credential manager.")
        updateBackupKeySaveState(newState = null)
      }

      is CredentialManagerResult.Interrupted -> {
        Log.i(TAG, "Retry saving backup key to credential manager after interruption.", result.exception)
        updateBackupKeySaveState(newState = BackupKeySaveState.AwaitingCredentialManager(isRetry = true))
      }

      is CredentialManagerError.MissingCredentialManager -> {
        Log.w(TAG, "Error saving backup key to credential manager: no credential manager is configured.", result.exception)
        updateBackupKeySaveState(newState = BackupKeySaveState.Error(result))
      }

      is CredentialManagerError.Unexpected -> {
        throw result.exception.logW(TAG, "Unexpected error when saving backup key to credential manager.")
      }
    }
  }
}

/** Represents state related to saving a backup key to the device password manager. */
sealed interface BackupKeySaveState {
  /** Awaiting the user to confirm they want to save the backup key. */
  data object RequestingConfirmation : BackupKeySaveState

  /** Awaiting the password manager's response for the backup key save operation. */
  data class AwaitingCredentialManager(val isRetry: Boolean) : BackupKeySaveState
  data object Success : BackupKeySaveState
  data class Error(val errorType: CredentialManagerError) : BackupKeySaveState
}

sealed interface CredentialManagerResult {
  data object Success : CredentialManagerResult
  data object UserCanceled : CredentialManagerResult

  /** The backup key save operation was interrupted and should be retried. */
  data class Interrupted(val exception: Exception) : CredentialManagerResult
}

sealed class CredentialManagerError : CredentialManagerResult {
  abstract val exception: Exception

  /** No password manager is configured on the device. */
  data class MissingCredentialManager(override val exception: Exception) : CredentialManagerError()
  data class Unexpected(override val exception: Exception) : CredentialManagerError()
}
