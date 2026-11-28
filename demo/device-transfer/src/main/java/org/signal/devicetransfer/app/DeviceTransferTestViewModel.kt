/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.devicetransfer.app

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.devicetransfer.TransferStatus.TransferMode
import org.signal.devicetransfer.app.DeviceTransferTestState.Dialog
import org.signal.devicetransfer.app.DeviceTransferTestState.LogEntry
import org.signal.devicetransfer.app.DeviceTransferTestState.Progress
import org.signal.devicetransfer.app.DeviceTransferTestState.Role
import org.signal.devicetransfer.app.DeviceTransferTestState.TransferResult

class DeviceTransferTestViewModel(
  private val repository: DeviceTransferTestRepository
) : EventDrivenViewModel<DeviceTransferTestEvent>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(DeviceTransferTestViewModel::class)
    private const val MAX_LOG_ENTRIES = 500
  }

  private val _state = MutableStateFlow(
    DeviceTransferTestState(
      deviceDescription = repository.deviceDescription,
      libsignalVersion = repository.libsignalVersion,
      readiness = repository.getReadiness()
    )
  )
  private val _actions = Channel<DeviceTransferTestAction>(Channel.BUFFERED)

  val state: StateFlow<DeviceTransferTestState> = _state.asStateFlow()
  val actions: Flow<DeviceTransferTestAction> = _actions.receiveAsFlow()

  init {
    repository.transferStatuses()
      .onEach { onEvent(DeviceTransferTestEvent.TransferStatusReceived(it.transferMode, it.authenticationCode)) }
      .launchIn(viewModelScope)

    repository.taskUpdates()
      .onEach { onEvent(DeviceTransferTestEvent.TaskUpdateReceived(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: DeviceTransferTestEvent) {
    when (event) {
      DeviceTransferTestEvent.ScreenResumed,
      DeviceTransferTestEvent.PermissionsResultReceived -> {
        refreshReadiness()
      }
      DeviceTransferTestEvent.GrantPermissionsClicked -> {
        val missing = repository.getMissingPermissions()
        if (missing.isNotEmpty()) {
          _actions.send(DeviceTransferTestAction.RequestPermissions(missing))
        }
      }
      is DeviceTransferTestEvent.PayloadSizeSelected -> {
        _state.update { it.copy(payloadSize = event.size) }
      }
      DeviceTransferTestEvent.StartServerClicked -> {
        if (!_state.value.canStart) return
        startRole(Role.SERVER)
      }
      DeviceTransferTestEvent.StartClientClicked -> {
        if (!_state.value.canStart) return
        startRole(Role.CLIENT)
      }
      DeviceTransferTestEvent.RestartClicked -> {
        val role = _state.value.role
        if (role == Role.NONE) return
        log("Restart: stop then start $role")
        repository.stop()
        startRole(role)
      }
      DeviceTransferTestEvent.StopClicked -> {
        log("Stop")
        repository.stop()
        _state.update { it.copy(role = Role.NONE, dialog = Dialog.None) }
      }
      DeviceTransferTestEvent.ClearLogClicked -> {
        _state.update { it.copy(log = emptyList()) }
      }
      is DeviceTransferTestEvent.VerificationAnswered -> {
        log(if (event.codesMatch) "Verification code confirmed" else "Verification code rejected")
        repository.setVerified(event.codesMatch)
        _state.update { it.copy(dialog = Dialog.None) }
      }
      is DeviceTransferTestEvent.TransferStatusReceived -> {
        applyTransferStatus(event.mode, event.authenticationCode)
      }
      is DeviceTransferTestEvent.TaskUpdateReceived -> {
        applyTaskUpdate(event.update)
      }
    }
  }

  private fun refreshReadiness() {
    _state.update { it.copy(readiness = repository.getReadiness()) }
  }

  private fun startRole(role: Role) {
    when (role) {
      Role.SERVER -> {
        log("Start server")
        repository.startServer()
      }
      Role.CLIENT -> {
        val size = _state.value.payloadSize
        log("Start client, sending ${size.bytes} bytes")
        repository.startClient(size.bytes)
      }
      Role.NONE -> return
    }

    _state.update { it.copy(role = role, progress = null, result = null, dialog = Dialog.None) }
  }

  private fun applyTransferStatus(mode: TransferMode, authenticationCode: Int) {
    log("Status: $mode")

    _state.update { state ->
      val dialog = when {
        mode == TransferMode.VERIFICATION_REQUIRED -> Dialog.Verification(authenticationCode)
        state.dialog is Dialog.Verification && mode in setOf(TransferMode.FAILED, TransferMode.SHUTDOWN) -> Dialog.None
        else -> state.dialog
      }

      state.copy(transferMode = mode, dialog = dialog)
    }
  }

  private fun applyTaskUpdate(update: TestTaskUpdate) {
    when (update) {
      is TestTaskUpdate.Progress -> {
        _state.update { it.copy(progress = Progress(update.bytes, update.totalBytes, update.elapsedMillis)) }
      }
      is TestTaskUpdate.Sent -> {
        log("Sent ${update.bytes} bytes in ${update.durationMillis} ms")
        _state.update { it.copy(result = TransferResult.Sent(update.bytes, update.durationMillis)) }
      }
      is TestTaskUpdate.Received -> {
        log("Received ${update.bytes} bytes in ${update.durationMillis} ms, checksum matches: ${update.checksumMatches}")
        _state.update { it.copy(result = TransferResult.Received(update.bytes, update.durationMillis, update.checksumMatches)) }
      }
      is TestTaskUpdate.Failed -> {
        log("Task failed: ${update.message}")
        _state.update { it.copy(result = TransferResult.Failed(update.message)) }
      }
    }
  }

  private fun log(message: String) {
    Log.i(TAG, message)
    _state.update { it.copy(log = (it.log + LogEntry(System.currentTimeMillis(), message)).takeLast(MAX_LOG_ENTRIES)) }
  }
}
