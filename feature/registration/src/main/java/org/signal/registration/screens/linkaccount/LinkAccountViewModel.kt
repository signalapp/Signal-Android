/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.linkaccount

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.ui.compose.QrCodeData
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.LinkAndSyncWaitResult
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.quickrestore.QrState
import org.signal.registration.screens.util.navigateTo

/**
 * Handles creating and maintaining a provisioning websocket in pursuit of adding this device as a
 * linked (secondary) device, then registering it once the primary scans the QR code.
 */
class LinkAccountViewModel(
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  showCreateAccount: Boolean = true
) : EventDrivenViewModel<LinkAccountScreenEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(LinkAccountViewModel::class)
    private const val DEVICE_NAME = "Android"
  }

  private val _state = MutableStateFlow(LinkAccountScreenState(showCreateAccount = showCreateAccount))
  val state: StateFlow<LinkAccountScreenState> = _state.asStateFlow()

  private var provisioningJob: Job? = null

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    startProvisioning()
  }

  override suspend fun processEvent(event: LinkAccountScreenEvent) {
    applyEvent(_state.value, event) { _state.value = it }
  }

  @VisibleForTesting
  fun applyEvent(state: LinkAccountScreenState, event: LinkAccountScreenEvent, stateEmitter: (LinkAccountScreenState) -> Unit) {
    val result = when (event) {
      LinkAccountScreenEvent.GetHelpClick -> error("This event is handled in the nav-entry.")
      LinkAccountScreenEvent.CreateAccountClick -> {
        // Revisit permission screen if necessary
        if (parentState.value.backStack.any { it == RegistrationRoute.PhoneNumberEntry }) {
          parentEventEmitter(RegistrationFlowEvent.NavigateBackToScreen(RegistrationRoute.PhoneNumberEntry))
        } else {
          parentEventEmitter.navigateTo(RegistrationRoute.Permissions(nextRoute = RegistrationRoute.PhoneNumberEntry), popCurrent = true)
        }
        state
      }
      LinkAccountScreenEvent.DisplayOverlayClick -> state.copy(displayQrOverlay = true)
      LinkAccountScreenEvent.HideOverlayClick -> state.copy(displayQrOverlay = false)
      LinkAccountScreenEvent.RetryQrCode, LinkAccountScreenEvent.DismissError -> {
        startProvisioning()
        state.copy(qrCodeState = QrState.Loading, showError = false)
      }
      LinkAccountScreenEvent.ConfirmDeleteAndRelink -> {
        viewModelScope.launch { repository.clearLocalDataAndRestart() }
        state.copy(showDeleteDataDialog = false)
      }
      LinkAccountScreenEvent.CancelDeleteAndRelink -> {
        startProvisioning()
        state.copy(qrCodeState = QrState.Loading, showDeleteDataDialog = false)
      }
    }
    stateEmitter(result)
  }

  private fun startProvisioning() {
    provisioningJob?.cancel()
    provisioningJob = viewModelScope.launch {
      repository.startLinkDeviceProvisioning().collect { event ->
        when (event) {
          is NetworkController.LinkDeviceProvisioningEvent.QrCodeReady -> {
            Log.d(TAG, "[Provisioning] QR code ready")
            _state.update {
              it.copy(qrCodeState = QrState.Loaded(qrCodeData = QrCodeData.forData(data = event.url, supportIconOverlay = false)))
            }
          }
          is NetworkController.LinkDeviceProvisioningEvent.MessageReceived -> {
            Log.i(TAG, "[Provisioning] Message received from primary device")
            handleProvisioningMessage(event.message)
          }
          is NetworkController.LinkDeviceProvisioningEvent.Error -> {
            Log.w(TAG, "[Provisioning] Error", event.cause)
            _state.update { it.copy(qrCodeState = QrState.Failed) }
          }
        }
      }
    }
  }

  private suspend fun handleProvisioningMessage(message: NetworkController.LinkDeviceProvisioningMessage) {
    if (repository.isProvisioningForDifferentAccount(message)) {
      Log.w(TAG, "[Register] Provisioning message is for a different account prompting to delete local data")
      _state.update { it.copy(isRegistering = false, showDeleteDataDialog = true) }
      return
    }

    val isCleanStart = repository.isCleanStart()

    _state.update { it.copy(isRegistering = true, qrCodeState = QrState.Scanned) }

    when (val result = repository.registerAsLinkedDevice(message, DEVICE_NAME)) {
      is RequestResult.Success -> {
        Log.i(TAG, "[Register] Success! hasLinkAndSyncBackup: ${result.result.hasLinkAndSyncBackup}, isCleanStart: $isCleanStart")

        if (result.result.hasLinkAndSyncBackup && !isCleanStart) {
          Log.w(TAG, "[Register] Link-and-sync offered on a relink over existing data, skipping import")
        }

        if (result.result.hasLinkAndSyncBackup && isCleanStart) {
          // Wait here until the primary actually makes the backup available or tells us not to expect one
          _state.update { it.copy(isRegistering = false, isWaitingForPrimary = true) }
          val waitResult = repository.awaitLinkAndSyncArchive()
          _state.update { it.copy(isWaitingForPrimary = false) }

          when (waitResult) {
            is LinkAndSyncWaitResult.ArchiveAvailable -> {
              // Backup is ready: hand off to the MessageSync screen to download + apply it
              parentEventEmitter.navigateTo(RegistrationRoute.MessageSync)
            }
            LinkAndSyncWaitResult.ContinueWithoutBackup -> {
              // The primary declined to sync or never delivered a backup. The device is still linked, so restore from storage service and finish.
              Log.w(TAG, "[Register] Link-and-sync offered but continuing without a backup.")
              repository.restoreLinkedDeviceFromStorageService()
              parentEventEmitter.navigateTo(RegistrationRoute.FullyComplete)
            }
            LinkAndSyncWaitResult.RelinkRequired -> {
              // The primary asked us to re-link, which leaves our partial local registration invalid. Wipe everything and restart.
              Log.w(TAG, "[Register] Primary requested re-link; wiping local data and restarting.")
              repository.clearLocalDataAndRestart()
            }
          }
        } else {
          // No valid link-and-sync backup, restore from storage service immediately, then finish
          repository.restoreLinkedDeviceFromStorageService()
          _state.update { it.copy(isRegistering = false) }
          parentEventEmitter.navigateTo(RegistrationRoute.FullyComplete)
        }
      }
      is RequestResult.NonSuccess -> {
        Log.w(TAG, "[Register] Failed: ${result.error::class.simpleName}")
        showRegistrationError()
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[Register] Network error.", result.networkError)
        showRegistrationError()
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[Register] Application error.", result.cause)
        showRegistrationError()
      }
    }
  }

  private fun showRegistrationError() {
    _state.update { it.copy(isRegistering = false, showError = true) }
  }

  override fun onCleared() {
    provisioningJob?.cancel()
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val showCreateAccount: Boolean = true
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return LinkAccountViewModel(repository, parentState, parentEventEmitter, showCreateAccount) as T
    }
  }
}
