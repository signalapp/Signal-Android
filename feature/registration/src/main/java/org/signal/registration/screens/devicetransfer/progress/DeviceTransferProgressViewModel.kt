/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.progress

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.devicetransfer.DeviceToDeviceTransferService
import org.signal.devicetransfer.NewDeviceRestoreStatus
import org.signal.devicetransfer.TransferStatus
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo

class DeviceTransferProgressViewModel(
  private val context: Context,
  private val progressEvents: Flow<NewDeviceRestoreStatus>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<DeviceTransferProgressScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(DeviceTransferProgressViewModel::class)

    /** Cold Flow over [NewDeviceRestoreStatus] EventBus events. */
    fun restoreStatusFlow(): Flow<NewDeviceRestoreStatus> = callbackFlow {
      val subscriber = object {
        @Subscribe(threadMode = ThreadMode.MAIN)
        fun onEventMainThread(event: NewDeviceRestoreStatus) {
          trySend(event)
        }
      }
      EventBus.getDefault().register(subscriber)
      awaitClose { EventBus.getDefault().unregister(subscriber) }
    }.flowOn(Dispatchers.Main.immediate)
  }

  private val _state = MutableStateFlow(DeviceTransferProgressState())
  val state: StateFlow<DeviceTransferProgressState> = _state

  private val _showCancelDialog = MutableStateFlow(false)
  val showCancelDialog: StateFlow<Boolean> = _showCancelDialog

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    viewModelScope.launch {
      progressEvents.collect { handleProgressEvent(it) }
    }
  }

  override suspend fun processEvent(event: DeviceTransferProgressScreenEvents) {
    applyEvent(state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: DeviceTransferProgressState,
    event: DeviceTransferProgressScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (DeviceTransferProgressState) -> Unit
  ) {
    when (event) {
      DeviceTransferProgressScreenEvents.CancelClicked -> {
        _showCancelDialog.value = true
      }
      DeviceTransferProgressScreenEvents.CancelDismissed -> {
        _showCancelDialog.value = false
      }
      DeviceTransferProgressScreenEvents.CancelConfirmed -> {
        _showCancelDialog.value = false
        stopService()
        parentEventEmitter.navigateBack()
      }
      DeviceTransferProgressScreenEvents.TryAgainClicked -> {
        stopService()
        parentEventEmitter.navigateTo(RegistrationRoute.DeviceTransferInstructions)
      }
    }
  }

  private fun handleProgressEvent(event: NewDeviceRestoreStatus) {
    when (event.state) {
      NewDeviceRestoreStatus.State.IN_PROGRESS -> {
        _state.value = _state.value.copy(messageCount = event.messageCount, status = DeviceTransferProgressState.Status.RECEIVING)
      }
      NewDeviceRestoreStatus.State.TRANSFER_COMPLETE -> {
        _state.value = _state.value.copy(messageCount = event.messageCount, status = DeviceTransferProgressState.Status.IMPORTING)
      }
      NewDeviceRestoreStatus.State.RESTORE_COMPLETE -> {
        _state.value = _state.value.copy(status = DeviceTransferProgressState.Status.FINALIZING)
        stopService()
        parentEventEmitter.navigateTo(RegistrationRoute.DeviceTransferComplete)
      }
      NewDeviceRestoreStatus.State.FAILURE_VERSION_DOWNGRADE -> {
        _state.value = _state.value.copy(status = DeviceTransferProgressState.Status.FAILED, errorReason = DeviceTransferProgressState.ErrorReason.VERSION_DOWNGRADE)
        stopService()
      }
      NewDeviceRestoreStatus.State.FAILURE_FOREIGN_KEY -> {
        _state.value = _state.value.copy(status = DeviceTransferProgressState.Status.FAILED, errorReason = DeviceTransferProgressState.ErrorReason.FOREIGN_KEY)
        stopService()
      }
      NewDeviceRestoreStatus.State.FAILURE_UNKNOWN -> {
        _state.value = _state.value.copy(status = DeviceTransferProgressState.Status.FAILED, errorReason = DeviceTransferProgressState.ErrorReason.UNKNOWN)
        stopService()
      }
    }
  }

  private fun stopService() {
    DeviceToDeviceTransferService.stop(context)
    EventBus.getDefault().removeStickyEvent(TransferStatus::class.java)
  }

  class Factory(
    private val context: Context,
    private val progressEvents: Flow<NewDeviceRestoreStatus>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DeviceTransferProgressViewModel(context, progressEvents, parentEventEmitter) as T
    }
  }
}
