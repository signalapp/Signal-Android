/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.registration.NetworkController
import org.signal.registration.StorageController
import org.signal.registration.sample.storage.RegistrationPreferences

class MainScreenViewModel(
  private val storageController: StorageController,
  private val networkController: NetworkController,
  private val onLaunchRegistration: () -> Unit,
  private val onTransferAccount: () -> Unit,
  private val onOpenPinSettings: () -> Unit
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(MainScreenViewModel::class)
  }

  private val _state = MutableStateFlow(MainScreenState())
  val state: StateFlow<MainScreenState> = _state.asStateFlow()

  init {
    loadRegistrationData()
  }

  fun refreshData() {
    loadRegistrationData()
  }

  fun onEvent(event: MainScreenEvents) {
    viewModelScope.launch {
      when (event) {
        MainScreenEvents.LaunchRegistration -> onLaunchRegistration()
        MainScreenEvents.TransferAccount -> onTransferAccount()
        MainScreenEvents.OpenPinSettings -> onOpenPinSettings()
        MainScreenEvents.ClearAllData -> {
          storageController.clearAllData()
          refreshData()
        }
      }
    }
  }

  private fun loadRegistrationData() {
    viewModelScope.launch {
      val existingData = storageController.getPreExistingRegistrationData()
      _state.value = _state.value.copy(
        existingRegistrationState = if (existingData != null) {
          MainScreenState.ExistingRegistrationState(
            phoneNumber = existingData.e164,
            aci = existingData.aci.toString(),
            pni = existingData.pni.toStringWithoutPrefix(),
            aep = existingData.aep.value,
            pin = RegistrationPreferences.pin,
            registrationLockEnabled = RegistrationPreferences.registrationLockEnabled,
            pinsOptedOut = RegistrationPreferences.pinsOptedOut,
            temporaryMasterKey = RegistrationPreferences.temporaryMasterKey?.let {
              Base64.encodeWithPadding(it.serialize())
            }
          )
        } else {
          null
        },
        registrationExpired = false
      )

      if (existingData != null) {
        checkRegistrationStatus()
      }
    }
  }

  private suspend fun checkRegistrationStatus() {
    when (val result = networkController.getSvrCredentials()) {
      is NetworkController.RegistrationNetworkResult.Success -> {
        Log.d(TAG, "[CheckRegistration] Still registered.")
      }
      is NetworkController.RegistrationNetworkResult.Failure -> {
        when (result.error) {
          NetworkController.GetSvrCredentialsError.Unauthorized -> {
            Log.w(TAG, "[CheckRegistration] No longer registered (401).")
            _state.value = _state.value.copy(registrationExpired = true)
          }
          NetworkController.GetSvrCredentialsError.NoServiceCredentialsAvailable -> {
            Log.w(TAG, "[CheckRegistration] No credentials available locally.")
            _state.value = _state.value.copy(registrationExpired = true)
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        Log.w(TAG, "[CheckRegistration] Network error, can't verify status.", result.exception)
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        Log.w(TAG, "[CheckRegistration] Application error, can't verify status.", result.exception)
      }
    }
  }

  class Factory(
    private val storageController: StorageController,
    private val networkController: NetworkController,
    private val onLaunchRegistration: () -> Unit,
    private val onTransferAccount: () -> Unit,
    private val onOpenPinSettings: () -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MainScreenViewModel(storageController, networkController, onLaunchRegistration, onTransferAccount, onOpenPinSettings) as T
    }
  }
}
