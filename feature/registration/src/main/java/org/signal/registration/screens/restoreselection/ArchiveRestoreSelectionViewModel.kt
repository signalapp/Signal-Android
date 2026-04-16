/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restoreselection

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.signal.core.util.logging.Log
import org.signal.registration.PendingRestoreOption
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.EventDrivenViewModel
import org.signal.registration.screens.util.navigateTo

/**
 * A view model to be used with [ArchiveRestoreSelectionScreen] after a quick restore.
 * To avoid spinners, we'll have the quick restore screen determine if a remote backup
 * is available and tell us.
 */
class ArchiveRestoreSelectionViewModel(
  private val restoreOptions: List<ArchiveRestoreOption>,
  private val isPreRegistration: Boolean,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<ArchiveRestoreSelectionScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(ArchiveRestoreSelectionViewModel::class)
  }

  private val _localState = MutableStateFlow(
    ArchiveRestoreSelectionState(
      restoreOptions = restoreOptions
    )
  )

  val state: StateFlow<ArchiveRestoreSelectionState> = _localState

  override suspend fun processEvent(event: ArchiveRestoreSelectionScreenEvents) {
    applyEvent(state.value, event) { _localState.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: ArchiveRestoreSelectionState, event: ArchiveRestoreSelectionScreenEvents, stateEmitter: (ArchiveRestoreSelectionState) -> Unit) {
    val result = when (event) {
      is ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected -> {
        when (event.option) {
          ArchiveRestoreOption.SignalSecureBackup -> {
            if (isPreRegistration) {
              parentEventEmitter(RegistrationFlowEvent.PendingRestoreOptionSelected(PendingRestoreOption.RemoteBackup))
              parentEventEmitter.navigateTo(RegistrationRoute.PhoneNumberEntry)
            } else {
              parentEventEmitter.navigateTo(RegistrationRoute.EnterAepForRemoteBackupPostRegistration)
            }
            state
          }
          ArchiveRestoreOption.LocalBackup -> {
            if (isPreRegistration) {
              parentEventEmitter(RegistrationFlowEvent.PendingRestoreOptionSelected(PendingRestoreOption.LocalBackup))
              parentEventEmitter.navigateTo(RegistrationRoute.PhoneNumberEntry)
            } else {
              parentEventEmitter.navigateTo(RegistrationRoute.LocalBackupRestore(isPreRegistration = false))
            }
            state
          }
          ArchiveRestoreOption.DeviceTransfer -> {
            Log.w(TAG, "Device transfer not yet implemented")
            state
          }
          ArchiveRestoreOption.None -> {
            state.copy(showSkipWarningDialog = true)
          }
        }
      }
      is ArchiveRestoreSelectionScreenEvents.Skip -> {
        state.copy(showSkipWarningDialog = true)
      }
      is ArchiveRestoreSelectionScreenEvents.ConfirmSkip -> {
        parentEventEmitter.navigateTo(RegistrationRoute.PinCreate)
        state.copy(showSkipWarningDialog = false)
      }
      is ArchiveRestoreSelectionScreenEvents.DismissSkipWarning -> {
        state.copy(showSkipWarningDialog = false)
      }
    }
    stateEmitter(result)
  }

  class Factory(
    private val restoreOptions: List<ArchiveRestoreOption>,
    private val isPreRegistration: Boolean,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return ArchiveRestoreSelectionViewModel(restoreOptions, isPreRegistration, parentEventEmitter) as T
    }
  }
}
