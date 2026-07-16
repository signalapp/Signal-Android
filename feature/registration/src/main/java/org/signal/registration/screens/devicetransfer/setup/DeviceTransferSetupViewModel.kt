/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.setup

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
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
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.devicetransfer.DeviceToDeviceTransferService
import org.signal.devicetransfer.TransferStatus
import org.signal.devicetransfer.WifiDirect
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo
import kotlin.time.Duration.Companion.seconds

class DeviceTransferSetupViewModel(
  private val context: Context,
  private val networkController: NetworkController,
  private val setupEvents: Flow<TransferStatus>,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<DeviceTransferSetupScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(DeviceTransferSetupViewModel::class)
    private val PREPARE_TAKING_TOO_LONG = 30.seconds
    private val WAITING_TAKING_TOO_LONG = 90.seconds

    /** Cold Flow over sticky [TransferStatus] EventBus events. */
    fun transferStatusFlow(): Flow<TransferStatus> = callbackFlow {
      val subscriber = object {
        @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
        fun onEventMainThread(event: TransferStatus) {
          trySend(event)
        }
      }
      EventBus.getDefault().register(subscriber)
      awaitClose { EventBus.getDefault().unregister(subscriber) }
    }.flowOn(Dispatchers.Main.immediate)
  }

  private val _state = MutableStateFlow(DeviceTransferSetupState())
  val state: StateFlow<DeviceTransferSetupState> = _state

  private var takingTooLongJob: Job? = null
  private var setupEventsJob: Job? = null
  private var shutdown: Boolean = false

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    subscribeToSetupEvents()
    onEvent(DeviceTransferSetupScreenEvents.CheckPermissions)
  }

  override suspend fun processEvent(event: DeviceTransferSetupScreenEvents) {
    applyEvent(state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: DeviceTransferSetupState,
    event: DeviceTransferSetupScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (DeviceTransferSetupState) -> Unit
  ) {
    when (event) {
      DeviceTransferSetupScreenEvents.CheckPermissions -> {
        shutdown = false
        cancelTakingTooLong()
        if (isLocationPermissionGranted()) {
          stateEmitter(state.copy(step = SetupStep.LOCATION_CHECK, takingTooLong = false))
          checkLocation(parentEventEmitter, stateEmitter)
        } else {
          stateEmitter(state.copy(step = SetupStep.PERMISSIONS_CHECK, takingTooLong = false, pendingActions = state.pendingActions.copy(requestLocationPermission = true)))
        }
      }

      DeviceTransferSetupScreenEvents.PermissionsGranted -> {
        stateEmitter(state.copy(step = SetupStep.LOCATION_CHECK))
        checkLocation(parentEventEmitter, stateEmitter)
      }

      DeviceTransferSetupScreenEvents.PermissionsDenied -> {
        stateEmitter(state.copy(step = SetupStep.PERMISSIONS_DENIED))
      }

      DeviceTransferSetupScreenEvents.RequestPermissionClicked -> {
        stateEmitter(state.copy(pendingActions = state.pendingActions.copy(requestLocationPermission = true)))
      }

      DeviceTransferSetupScreenEvents.OpenLocationSettingsClicked -> {
        stateEmitter(state.copy(pendingActions = state.pendingActions.copy(openLocationSettings = true)))
      }

      DeviceTransferSetupScreenEvents.OpenWifiSettingsClicked -> {
        stateEmitter(state.copy(pendingActions = state.pendingActions.copy(openWifiSettings = true)))
      }

      DeviceTransferSetupScreenEvents.OpenAppSettingsClicked -> {
        stateEmitter(state.copy(pendingActions = state.pendingActions.copy(openAppSettings = true)))
      }

      DeviceTransferSetupScreenEvents.OnResume -> {
        when (state.step) {
          SetupStep.WIFI_DISABLED -> {
            stateEmitter(state.copy(step = SetupStep.WIFI_CHECK))
            checkWifi(parentEventEmitter, stateEmitter)
          }
          SetupStep.LOCATION_DISABLED -> {
            stateEmitter(state.copy(step = SetupStep.LOCATION_CHECK))
            checkLocation(parentEventEmitter, stateEmitter)
          }
          else -> Unit
        }
      }

      DeviceTransferSetupScreenEvents.UserVerifiedCode -> {
        DeviceToDeviceTransferService.setAuthenticationCodeVerified(context, true)
        stateEmitter(state.copy(step = SetupStep.WAITING_FOR_OTHER_TO_VERIFY))
      }

      DeviceTransferSetupScreenEvents.UserRejectedCode -> {
        stateEmitter(state.copy(showVerifyRejectDialog = true))
      }

      DeviceTransferSetupScreenEvents.VerifyRejectConfirmed -> {
        DeviceToDeviceTransferService.setAuthenticationCodeVerified(context, false)
        stopService()
        stateEmitter(state.copy(showVerifyRejectDialog = false))
        parentEventEmitter.navigateBack()
      }

      DeviceTransferSetupScreenEvents.VerifyRejectDismissed -> {
        stateEmitter(state.copy(showVerifyRejectDialog = false))
      }

      DeviceTransferSetupScreenEvents.RetryClicked -> {
        shutdown = false
        stopService()
        subscribeToSetupEvents()
        stateEmitter(DeviceTransferSetupState(step = SetupStep.PERMISSIONS_CHECK))
        onEvent(DeviceTransferSetupScreenEvents.CheckPermissions)
      }

      DeviceTransferSetupScreenEvents.BackClicked -> {
        stopService()
        parentEventEmitter.navigateBack()
      }

      DeviceTransferSetupScreenEvents.RequestLocationPermissionHandled -> {
        stateEmitter(state.copy(pendingActions = state.pendingActions.copy(requestLocationPermission = false)))
      }

      DeviceTransferSetupScreenEvents.OpenLocationSettingsHandled -> {
        stateEmitter(state.copy(pendingActions = state.pendingActions.copy(openLocationSettings = false)))
      }

      DeviceTransferSetupScreenEvents.OpenWifiSettingsHandled -> {
        stateEmitter(state.copy(pendingActions = state.pendingActions.copy(openWifiSettings = false)))
      }

      DeviceTransferSetupScreenEvents.OpenAppSettingsHandled -> {
        stateEmitter(state.copy(pendingActions = state.pendingActions.copy(openAppSettings = false)))
      }
    }
  }

  private suspend fun checkLocation(
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (DeviceTransferSetupState) -> Unit
  ) {
    val locationRequired = Build.VERSION.SDK_INT < 33
    if (!locationRequired || isLocationEnabled()) {
      stateEmitter(_state.value.copy(step = SetupStep.WIFI_CHECK))
      checkWifi(parentEventEmitter, stateEmitter)
    } else {
      stateEmitter(_state.value.copy(step = SetupStep.LOCATION_DISABLED))
    }
  }

  private fun checkWifi(
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (DeviceTransferSetupState) -> Unit
  ) {
    if (isWifiEnabled()) {
      stateEmitter(_state.value.copy(step = SetupStep.WIFI_DIRECT_CHECK))
      checkWifiDirect(parentEventEmitter, stateEmitter)
    } else {
      stateEmitter(_state.value.copy(step = SetupStep.WIFI_DISABLED))
    }
  }

  private fun checkWifiDirect(
    @Suppress("UNUSED_PARAMETER") parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (DeviceTransferSetupState) -> Unit
  ) {
    when (WifiDirect.getAvailability(context)) {
      WifiDirect.AvailableStatus.AVAILABLE -> {
        val aep: AccountEntropyPool? = parentState.value.accountEntropyPool
        if (aep == null) {
          Log.w(TAG, "No AEP in flow state — cannot start transfer server")
          stateEmitter(_state.value.copy(step = SetupStep.ERROR, showErrorDialog = true))
          return
        }
        stateEmitter(_state.value.copy(step = SetupStep.START))
        networkController.startNewDeviceTransferServer(context, aep)
        stateEmitter(_state.value.copy(step = SetupStep.SETTING_UP))
        scheduleSettingUpTooLong()
      }
      WifiDirect.AvailableStatus.REQUIRED_PERMISSION_NOT_GRANTED -> {
        stateEmitter(_state.value.copy(step = SetupStep.PERMISSIONS_CHECK))
      }
      else -> {
        stateEmitter(_state.value.copy(step = SetupStep.WIFI_DIRECT_UNAVAILABLE))
      }
    }
  }

  private fun handleSetupEvent(event: TransferStatus) {
    if (shutdown) return
    Log.i(TAG, "Handling setup event: ${event.transferMode}")
    when (event.transferMode) {
      TransferStatus.TransferMode.READY,
      TransferStatus.TransferMode.STARTING_UP -> {
        _state.value = _state.value.copy(step = SetupStep.SETTING_UP)
        scheduleSettingUpTooLong()
      }
      TransferStatus.TransferMode.DISCOVERY -> {
        _state.value = _state.value.copy(step = SetupStep.WAITING, takingTooLong = false)
        scheduleWaitingTooLong()
      }
      TransferStatus.TransferMode.VERIFICATION_REQUIRED -> {
        cancelTakingTooLong()
        _state.value = _state.value.copy(step = SetupStep.VERIFY, authenticationCode = event.authenticationCode, takingTooLong = false)
      }
      TransferStatus.TransferMode.SERVICE_CONNECTED -> {
        cancelTakingTooLong()
        _state.value = _state.value.copy(step = SetupStep.CONNECTED)
        parentEventEmitter.navigateTo(RegistrationRoute.DeviceTransferProgress)
      }
      TransferStatus.TransferMode.SHUTDOWN,
      TransferStatus.TransferMode.FAILED -> {
        cancelTakingTooLong()
        _state.value = _state.value.copy(step = SetupStep.ERROR, showErrorDialog = true)
      }
      TransferStatus.TransferMode.UNAVAILABLE,
      TransferStatus.TransferMode.NETWORK_CONNECTED,
      TransferStatus.TransferMode.SERVICE_DISCONNECTED -> Unit
    }
  }

  private fun subscribeToSetupEvents() {
    setupEventsJob?.cancel()
    setupEventsJob = viewModelScope.launch {
      setupEvents.collect { handleSetupEvent(it) }
    }
  }

  private fun scheduleSettingUpTooLong() {
    cancelTakingTooLong()
    takingTooLongJob = viewModelScope.launch {
      delay(PREPARE_TAKING_TOO_LONG)
      if (_state.value.step == SetupStep.SETTING_UP) {
        _state.value = _state.value.copy(takingTooLong = true)
      }
    }
  }

  private fun scheduleWaitingTooLong() {
    cancelTakingTooLong()
    takingTooLongJob = viewModelScope.launch {
      delay(WAITING_TAKING_TOO_LONG)
      if (_state.value.step == SetupStep.WAITING) {
        shutdown = true
        stopService()
        _state.value = _state.value.copy(step = SetupStep.TROUBLESHOOTING)
      }
    }
  }

  private fun cancelTakingTooLong() {
    takingTooLongJob?.cancel()
    takingTooLongJob = null
  }

  private fun stopService() {
    DeviceToDeviceTransferService.stop(context)
    EventBus.getDefault().removeStickyEvent(TransferStatus::class.java)
  }

  private fun isLocationPermissionGranted(): Boolean {
    return ContextCompat.checkSelfPermission(context, WifiDirect.requiredPermission()) == PackageManager.PERMISSION_GRANTED
  }

  private fun isLocationEnabled(): Boolean {
    val locationManager = ContextCompat.getSystemService(context, LocationManager::class.java) ?: return false
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
  }

  private fun isWifiEnabled(): Boolean {
    val wifiManager = ContextCompat.getSystemService(context, WifiManager::class.java) ?: return false
    return wifiManager.isWifiEnabled
  }

  override fun onCleared() {
    super.onCleared()
    cancelTakingTooLong()
    setupEventsJob?.cancel()
  }

  class Factory(
    private val context: Context,
    private val networkController: NetworkController,
    private val setupEvents: Flow<TransferStatus>,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DeviceTransferSetupViewModel(context, networkController, setupEvents, parentState, parentEventEmitter) as T
    }
  }
}
