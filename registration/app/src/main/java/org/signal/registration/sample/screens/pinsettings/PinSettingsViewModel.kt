/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.screens.pinsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.signal.registration.NetworkController
import org.signal.registration.NetworkController.RegistrationNetworkResult
import org.signal.registration.sample.storage.RegistrationPreferences

/**
 * ViewModel for the PIN settings screen.
 *
 * Handles setting PIN via SVR backup and enabling/disabling registration lock.
 */
class PinSettingsViewModel(
  private val networkController: NetworkController,
  private val onBack: () -> Unit
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(PinSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(
    PinSettingsState(
      hasPinSet = RegistrationPreferences.hasPin,
      registrationLockEnabled = RegistrationPreferences.registrationLockEnabled
    )
  )
  val state: StateFlow<PinSettingsState> = _state.asStateFlow()

  fun onEvent(event: PinSettingsEvents) {
    when (event) {
      is PinSettingsEvents.SetPin -> {
        _state.value = _state.value.copy(loading = true)
        handleSetPin(event.pin)
        _state.value = _state.value.copy(loading = true)
      }
      is PinSettingsEvents.ToggleRegistrationLock -> {
        _state.value = _state.value.copy(loading = true)
        handleToggleRegistrationLock()
        _state.value = _state.value.copy(loading = false)
      }
      is PinSettingsEvents.Back -> onBack()
      is PinSettingsEvents.DismissMessage -> dismissMessage()
    }
  }

  private fun handleSetPin(pin: String) {
    if (pin.length < 4) {
      _state.value = _state.value.copy(toastMessage = "PIN must be at least 4 digits")
      return
    }

    viewModelScope.launch {
      // Generate or reuse existing master key
      val masterKey = RegistrationPreferences.masterKey ?: run {
        _state.value = _state.value.copy(toastMessage = "No master key found!")
        return@launch
      }

      when (val result = networkController.setPinAndMasterKeyOnSvr(pin, masterKey)) {
        is RegistrationNetworkResult.Success -> {
          Log.i(TAG, "Successfully backed up PIN to SVR")
          RegistrationPreferences.pin = pin
          _state.value = _state.value.copy(
            loading = false,
            hasPinSet = true,
            toastMessage = "PIN has been set successfully"
          )
        }
        is RegistrationNetworkResult.Failure -> {
          Log.w(TAG, "Failed to backup PIN: ${result.error}")
          _state.value = _state.value.copy(
            loading = false,
            toastMessage = "Failed to set PIN: ${result.error::class.simpleName}"
          )
        }
        is RegistrationNetworkResult.NetworkError -> {
          Log.w(TAG, "Network error while setting PIN", result.exception)
          _state.value = _state.value.copy(
            loading = false,
            toastMessage = "Network error. Please check your connection."
          )
        }
        is RegistrationNetworkResult.ApplicationError -> {
          Log.w(TAG, "Application error while setting PIN", result.exception)
          _state.value = _state.value.copy(
            loading = false,
            toastMessage = "An error occurred: ${result.exception.message}"
          )
        }
      }
    }
  }

  private fun handleToggleRegistrationLock() {
    val currentlyEnabled = _state.value.registrationLockEnabled

    viewModelScope.launch {
      val result = if (currentlyEnabled) {
        networkController.disableRegistrationLock()
      } else {
        networkController.enableRegistrationLock()
      }

      when (result) {
        is RegistrationNetworkResult.Success -> {
          val newEnabled = !currentlyEnabled
          RegistrationPreferences.registrationLockEnabled = newEnabled
          Log.i(TAG, "Registration lock ${if (newEnabled) "enabled" else "disabled"}")
          _state.value = _state.value.copy(
            loading = false,
            registrationLockEnabled = newEnabled,
            toastMessage = if (newEnabled) "Registration lock enabled" else "Registration lock disabled"
          )
        }
        is RegistrationNetworkResult.Failure -> {
          Log.w(TAG, "Failed to toggle registration lock: ${result.error}")
          _state.value = _state.value.copy(
            loading = false,
            toastMessage = "Failed to update registration lock"
          )
        }
        is RegistrationNetworkResult.NetworkError -> {
          Log.w(TAG, "Network error while toggling registration lock", result.exception)
          _state.value = _state.value.copy(
            loading = false,
            toastMessage = "Network error. Please check your connection."
          )
        }
        is RegistrationNetworkResult.ApplicationError -> {
          Log.w(TAG, "Application error while toggling registration lock", result.exception)
          _state.value = _state.value.copy(
            loading = false,
            toastMessage = "An error occurred: ${result.exception.message}"
          )
        }
      }
    }
  }

  private fun dismissMessage() {
    _state.value = _state.value.copy(toastMessage = null)
  }

  class Factory(
    private val networkController: NetworkController,
    private val onBack: () -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      @Suppress("UNCHECKED_CAST")
      return PinSettingsViewModel(networkController, onBack) as T
    }
  }
}
