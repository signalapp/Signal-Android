/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.messagesync

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateTo

/**
 * Drives the link-and-sync message backup restore that runs after this device is registered as a
 * linked device, surfacing download progress and completing registration when finished.
 */
class MessageSyncViewModel(
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<MessageSyncScreenEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(MessageSyncViewModel::class)
  }

  private val _state = MutableStateFlow(MessageSyncScreenState())
  val state: StateFlow<MessageSyncScreenState> = _state.asStateFlow()

  private var restoreJob: Job? = null
  private var finishJob: Job? = null

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    startRestore()
  }

  private fun startRestore() {
    restoreJob?.cancel()
    restoreJob = viewModelScope.launch {
      repository.restoreLinkAndSyncBackup().collect { progress ->
        when (progress) {
          is LinkAndSyncProgress.Waiting -> Unit
          is LinkAndSyncProgress.Downloading -> _state.update {
            it.copy(downloadedBytes = progress.bytesDownloaded, totalBytes = progress.totalBytes)
          }
          is LinkAndSyncProgress.Restoring -> _state.update { it.copy(isFinishing = true) }
          is LinkAndSyncProgress.Complete -> {
            Log.i(TAG, "[MessageSync] Link-and-sync complete; restoring from storage service then completing.")
            finish()
          }
          is LinkAndSyncProgress.Failed -> {
            Log.w(TAG, "[MessageSync] Link-and-sync failed; prompting the user to retry or continue without messages.", progress.cause)
            _state.update { it.copy(isFinishing = false, showSyncFailedDialog = true) }
          }
          is LinkAndSyncProgress.RelinkRequired -> {
            Log.w(TAG, "[MessageSync] Primary requested re-link; wiping local data and restarting.")
            repository.clearLocalDataAndRestart()
          }
        }
      }
    }
  }

  private fun finish(cancelDownload: Boolean = false) {
    if (finishJob != null) {
      return
    }

    finishJob = viewModelScope.launch {
      _state.update { it.copy(isFinishing = true) }
      if (cancelDownload) {
        restoreJob?.cancelAndJoin()
      }
      repository.restoreLinkedDeviceFromStorageService()
      parentEventEmitter.navigateTo(RegistrationRoute.FullyComplete)
    }
  }

  override suspend fun processEvent(event: MessageSyncScreenEvent) {
    applyEvent(_state.value, event) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: MessageSyncScreenState, event: MessageSyncScreenEvent, stateEmitter: (MessageSyncScreenState) -> Unit) {
    val result = when (event) {
      MessageSyncScreenEvent.LearnMoreClick -> error("This event is handled in the nav-entry.")
      MessageSyncScreenEvent.CancelClick -> {
        Log.i(TAG, "[MessageSync] User cancelled message sync; awaiting restore cancellation, then restoring from storage service and completing.")
        stateEmitter(state.copy(isFinishing = true))
        finish(cancelDownload = true)
        state.copy(isFinishing = true)
      }
      MessageSyncScreenEvent.RetryClick -> {
        Log.i(TAG, "[MessageSync] User retrying link-and-sync after a failure.")
        startRestore()
        state.copy(showSyncFailedDialog = false, isFinishing = false, downloadedBytes = 0.bytes, totalBytes = 0.bytes)
      }
      MessageSyncScreenEvent.ContinueWithoutMessagesClick -> {
        Log.i(TAG, "[MessageSync] User continuing without message history after a failed link-and-sync.")
        finish()
        state.copy(showSyncFailedDialog = false, isFinishing = true)
      }
    }
    stateEmitter(result)
  }

  override fun onCleared() {
    restoreJob?.cancel()
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MessageSyncViewModel(repository, parentState, parentEventEmitter) as T
    }
  }
}
