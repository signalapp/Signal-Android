/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateTo

class EnterAepForRemoteBackupPostRegistrationViewModel(
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(EnterAepForRemoteBackupPostRegistrationViewModel::class)
  }

  private val _state = MutableStateFlow(EnterAepState())
  val state: StateFlow<EnterAepState> = _state.asStateFlow()

  fun onEvent(event: EnterAepEvents) {
    Log.d(TAG, "[Event] $event")
    when (event) {
      is EnterAepEvents.BackupKeyChanged -> {
        _state.update { EnterAepScreenEventHandler.applyEvent(it, event) }
      }
      is EnterAepEvents.Submit -> {
        if (_state.value.isBackupKeyValid) {
          val aep = AccountEntropyPool(_state.value.backupKey)
          parentEventEmitter(RegistrationFlowEvent.UserSuppliedAepSubmitted(aep))
          parentEventEmitter.navigateTo(RegistrationRoute.RemoteRestore(aep))
        }
      }
      is EnterAepEvents.Cancel -> {
        parentEventEmitter(RegistrationFlowEvent.NavigateBack)
      }
      is EnterAepEvents.DismissError -> {
        _state.update { EnterAepScreenEventHandler.applyEvent(it, event) }
      }
    }
  }

  class Factory(
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return EnterAepForRemoteBackupPostRegistrationViewModel(parentEventEmitter) as T
    }
  }
}
