/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.welcome

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateTo

/**
 * Drives the welcome screen. It observes the parent flow state to decide whether to offer the restore-or-transfer
 * option (which depends on asynchronously-loaded pre-existing registration data) and handles the screen's navigation.
 */
class WelcomeScreenViewModel(
  repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val hasPermissions: () -> Boolean,
  private val getRequiredLinkedDevicePermission: () -> String?
) : EventDrivenViewModel<WelcomeScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(WelcomeScreenViewModel::class)
  }

  private val _state = MutableStateFlow(WelcomeScreenState(isLinkAndSyncAvailable = repository.isLinkAndSyncAvailable, showRestoreOrTransfer = false))
  val state: StateFlow<WelcomeScreenState> = _state.asStateFlow()

  private val _actions = Channel<WelcomeScreenActions>(Channel.BUFFERED)
  val actions: Flow<WelcomeScreenActions> = _actions.receiveAsFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    parentState
      .onEach { onEvent(WelcomeScreenEvents.ParentStateChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: WelcomeScreenEvents) {
    applyEvent(_state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  fun applyEvent(state: WelcomeScreenState, event: WelcomeScreenEvents, parentEventEmitter: (RegistrationFlowEvent) -> Unit, stateEmitter: (WelcomeScreenState) -> Unit) {
    when (event) {
      is WelcomeScreenEvents.ParentStateChanged -> stateEmitter(applyParentState(state, event.parentState))
      WelcomeScreenEvents.Continue -> navigateRequestingPermissions(RegistrationRoute.PhoneNumberEntry, parentEventEmitter)
      WelcomeScreenEvents.HasOldPhone -> navigateRequestingPermissions(RegistrationRoute.QuickRestoreQrScan, parentEventEmitter)
      WelcomeScreenEvents.DoesNotHaveOldPhone -> navigateRequestingPermissions(RegistrationRoute.ArchiveRestoreSelection.forManualRestore(), parentEventEmitter)
      WelcomeScreenEvents.LinkDevice -> {
        if (getRequiredLinkedDevicePermission().isNullOrBlank()) {
          parentEventEmitter.navigateTo(RegistrationRoute.LinkAccount())
        } else {
          parentEventEmitter.navigateTo(RegistrationRoute.AllowNotifications(RegistrationRoute.LinkAccount()))
        }
      }
      WelcomeScreenEvents.ViewTermsAndPrivacy -> _actions.trySend(WelcomeScreenActions.ViewTermsAndPrivacy)
    }
  }

  private fun applyParentState(state: WelcomeScreenState, parentState: RegistrationFlowState): WelcomeScreenState {
    if (parentState.isRestoringNavigationState) {
      return state
    }

    return state.copy(showRestoreOrTransfer = parentState.preExistingRegistrationData == null)
  }

  private fun navigateRequestingPermissions(nextRoute: RegistrationRoute, parentEventEmitter: (RegistrationFlowEvent) -> Unit) {
    if (hasPermissions()) {
      parentEventEmitter.navigateTo(nextRoute)
    } else {
      parentEventEmitter.navigateTo(RegistrationRoute.Permissions(nextRoute = nextRoute))
    }
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val hasPermissions: () -> Boolean,
    private val getRequiredLinkedDevicePermission: () -> String?
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return WelcomeScreenViewModel(repository, parentState, parentEventEmitter, hasPermissions, getRequiredLinkedDevicePermission) as T
    }
  }
}
