/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.quickrestore

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.QrCodeData
import org.signal.core.util.logging.Log
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo

class QuickRestoreQrViewModel(
  private val repository: RegistrationRepository,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(QuickRestoreQrViewModel::class)
  }

  private val _localState = MutableStateFlow(QuickRestoreQrState())
  val state: StateFlow<QuickRestoreQrState> = _localState.asStateFlow()

  private var provisioningJob: Job? = null

  init {
    startProvisioning()
  }

  fun onEvent(event: QuickRestoreQrEvents) {
    Log.d(TAG, "[Event] $event")
    viewModelScope.launch {
      val stateEmitter: (QuickRestoreQrState) -> Unit = { newState ->
        _localState.value = newState
      }
      applyEvent(state.value, event, stateEmitter)
    }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: QuickRestoreQrState, event: QuickRestoreQrEvents, stateEmitter: (QuickRestoreQrState) -> Unit) {
    val result = when (event) {
      is QuickRestoreQrEvents.RetryQrCode -> {
        startProvisioning()
        state.copy(qrState = QrState.Loading, showRegistrationError = false, errorMessage = null)
      }
      is QuickRestoreQrEvents.Cancel -> {
        parentEventEmitter.navigateBack()
        state
      }
      is QuickRestoreQrEvents.UseProxy -> {
        // TODO [registration] - Navigate to proxy settings
        state
      }
      is QuickRestoreQrEvents.DismissError -> {
        startProvisioning()
        state.copy(showRegistrationError = false, errorMessage = null)
      }
    }
    stateEmitter(result)
  }

  private fun startProvisioning() {
    provisioningJob?.cancel()
    provisioningJob = viewModelScope.launch {
      repository.startProvisioning().collect { event ->
        when (event) {
          is NetworkController.ProvisioningEvent.QrCodeReady -> {
            Log.d(TAG, "[Provisioning] QR code ready")
            _localState.value = _localState.value.copy(
              qrState = QrState.Loaded(
                qrCodeData = QrCodeData.forData(data = event.url, supportIconOverlay = false)
              )
            )
          }
          is NetworkController.ProvisioningEvent.MessageReceived -> {
            Log.i(TAG, "[Provisioning] Message received from old device (platform: ${event.message.platform}, tier: ${event.message.tier})")
            handleProvisioningMessage(event.message)
          }
          is NetworkController.ProvisioningEvent.Error -> {
            Log.w(TAG, "[Provisioning] Error", event.cause)
            _localState.value = _localState.value.copy(qrState = QrState.Failed)
          }
        }
      }
    }
  }

  private suspend fun handleProvisioningMessage(message: NetworkController.ProvisioningMessage) {
    if (message.platform == NetworkController.ProvisioningMessage.Platform.IOS && message.tier == null) {
      // iOS without a backup tier cannot do a quick restore — navigate to the choose-restore screen
      parentEventEmitter.navigateTo(RegistrationRoute.ChooseRestoreOptionBeforeRegistration)
      return
    }

    _localState.value = _localState.value.copy(isRegistering = true, qrState = QrState.Scanned)

    val registerResult = repository.registerAccountWithProvisioningData(message)

    when (registerResult) {
      is NetworkController.RegistrationNetworkResult.Success -> {
        val (response, keyMaterial) = registerResult.data
        Log.i(TAG, "[Register] Success! reregistration: ${response.reregistration}")
        parentEventEmitter(RegistrationFlowEvent.Registered(keyMaterial.accountEntropyPool))
        parentEventEmitter.navigateTo(RegistrationRoute.ChooseRestoreOptionAfterRegistration)
      }
      is NetworkController.RegistrationNetworkResult.Failure -> {
        when (registerResult.error) {
          is NetworkController.RegisterAccountError.RateLimited -> {
            Log.w(TAG, "[Register] Rate limited (retryAfter: ${registerResult.error.retryAfter}).")
            _localState.value = _localState.value.copy(
              isRegistering = false,
              showRegistrationError = true,
              errorMessage = null
            )
          }
          is NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect -> {
            Log.w(TAG, "[Register] Recovery password incorrect: ${registerResult.error.message}")
            _localState.value = _localState.value.copy(
              isRegistering = false,
              showRegistrationError = true,
              errorMessage = null
            )
          }
          is NetworkController.RegisterAccountError.RegistrationLock -> {
            Log.w(TAG, "[Register] Registration locked.")
            parentEventEmitter.navigateTo(
              RegistrationRoute.PinEntryForRegistrationLock(
                timeRemaining = registerResult.error.data.timeRemaining,
                svrCredentials = registerResult.error.data.svr2Credentials
              )
            )
          }
          is NetworkController.RegisterAccountError.SessionNotFoundOrNotVerified -> {
            Log.w(TAG, "[Register] Session not found or not verified: ${registerResult.error.message}")
            _localState.value = _localState.value.copy(
              isRegistering = false,
              showRegistrationError = true,
              errorMessage = null
            )
          }
          is NetworkController.RegisterAccountError.DeviceTransferPossible -> {
            Log.w(TAG, "[Register] Device transfer possible. Resetting.")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
          }
          is NetworkController.RegisterAccountError.InvalidRequest -> {
            Log.w(TAG, "[Register] Invalid request: ${registerResult.error.message}")
            _localState.value = _localState.value.copy(
              isRegistering = false,
              showRegistrationError = true,
              errorMessage = null
            )
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        Log.w(TAG, "[Register] Network error.", registerResult.exception)
        _localState.value = _localState.value.copy(
          isRegistering = false,
          showRegistrationError = true,
          errorMessage = null
        )
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        Log.w(TAG, "[Register] Application error.", registerResult.exception)
        _localState.value = _localState.value.copy(
          isRegistering = false,
          showRegistrationError = true,
          errorMessage = null
        )
      }
    }
  }

  override fun onCleared() {
    provisioningJob?.cancel()
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return QuickRestoreQrViewModel(repository, parentEventEmitter) as T
    }
  }
}
