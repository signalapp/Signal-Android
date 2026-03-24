/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restoreselection

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateTo

class ArchiveRestoreSelectionViewModel(
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(ArchiveRestoreSelectionViewModel::class)
  }

  private val _localState = MutableStateFlow(ArchiveRestoreSelectionState())
  val state = combine(_localState, parentState) { state, parentState -> applyParentState(state, parentState) }
    .onEach { Log.d(TAG, "[State] $it") }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArchiveRestoreSelectionState())

  init {
//    viewModelScope.launch {
//      val options = repository.isSignalSecureBackupAvailable()
//      _localState.value = _localState.value.copy(restoreOptions = options)
//    }
  }

  fun onEvent(event: ArchiveRestoreSelectionScreenEvents) {
    Log.d(TAG, "[Event] $event")
    viewModelScope.launch {
      val stateEmitter: (ArchiveRestoreSelectionState) -> Unit = { newState ->
        _localState.value = newState
      }
      applyEvent(state.value, event, stateEmitter)
    }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: ArchiveRestoreSelectionState, event: ArchiveRestoreSelectionScreenEvents, stateEmitter: (ArchiveRestoreSelectionState) -> Unit) {
    val result = when (event) {
      is ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected -> {
        Log.w(TAG, "Restore option selected: ${event.option}, but flow not yet implemented") // TODO [registration] - Handle restore option selection
        state
      }
      is ArchiveRestoreSelectionScreenEvents.Skip -> {
        state.copy(showSkipRestoreWarning = true)
      }
      is ArchiveRestoreSelectionScreenEvents.ConfirmSkip -> {
        parentEventEmitter.navigateTo(RegistrationRoute.PinCreate)
        state.copy(showSkipRestoreWarning = false)
      }
      is ArchiveRestoreSelectionScreenEvents.DismissSkipWarning -> {
        state.copy(showSkipRestoreWarning = false)
      }
    }
    stateEmitter(result)
  }

  @VisibleForTesting
  fun applyParentState(state: ArchiveRestoreSelectionState, parentState: RegistrationFlowState): ArchiveRestoreSelectionState {
    return state
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return ArchiveRestoreSelectionViewModel(repository, parentState, parentEventEmitter) as T
    }
  }
}
