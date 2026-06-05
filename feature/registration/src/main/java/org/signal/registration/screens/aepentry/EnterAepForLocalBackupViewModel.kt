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
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.screens.util.navigateBack

class EnterAepForLocalBackupViewModel(
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val resultBus: ResultEventBus,
  private val resultKey: String
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(EnterAepForLocalBackupViewModel::class)
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
          resultBus.sendResult(resultKey, _state.value.backupKey)
          parentEventEmitter.navigateBack()
        }
      }
      is EnterAepEvents.Cancel -> {
        parentEventEmitter.navigateBack()
      }
      is EnterAepEvents.DismissError -> {
        _state.update { EnterAepScreenEventHandler.applyEvent(it, event) }
      }
    }
  }

  class Factory(
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val resultBus: ResultEventBus,
    private val resultKey: String
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return EnterAepForLocalBackupViewModel(parentEventEmitter, resultBus, resultKey) as T
    }
  }
}
