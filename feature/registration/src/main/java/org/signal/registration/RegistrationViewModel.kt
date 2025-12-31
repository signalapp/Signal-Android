/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.Manifest
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.core.util.logging.Log
import kotlin.reflect.KClass

/**
 * ViewModel shared across the registration flow.
 * Manages state and logic for registration screens.
 */
class RegistrationViewModel(private val repository: RegistrationRepository, savedStateHandle: SavedStateHandle) : ViewModel() {

  companion object {
    private val TAG = Log.tag(RegistrationViewModel::class)
  }

  private var _state: MutableStateFlow<RegistrationFlowState> = savedStateHandle.getMutableStateFlow("registration_state", initialValue = RegistrationFlowState())
  val state: StateFlow<RegistrationFlowState> = _state.asStateFlow()

  val resultBus = ResultEventBus()

  fun onEvent(event: RegistrationFlowEvent) {
    _state.value = applyEvent(_state.value, event)
  }

  fun applyEvent(state: RegistrationFlowState, event: RegistrationFlowEvent): RegistrationFlowState {
    return when (event) {
      is RegistrationFlowEvent.ResetState -> RegistrationFlowState()
      is RegistrationFlowEvent.SessionUpdated -> state.copy(sessionMetadata = event.session)
      is RegistrationFlowEvent.E164Chosen -> state.copy(sessionE164 = event.e164)
      is RegistrationFlowEvent.Registered -> state.copy(accountEntropyPool = event.accountEntropyPool)
      is RegistrationFlowEvent.MasterKeyRestoredViaRegistrationLock -> state.copy(temporaryMasterKey = event.masterKey, registrationLockProof = event.masterKey.deriveRegistrationLock())
      is RegistrationFlowEvent.MasterKeyRestoredViaPostRegisterPinEntry -> state.copy(temporaryMasterKey = event.masterKey)
      is RegistrationFlowEvent.NavigateToScreen -> applyNavigationToScreenEvent(state, event)
      is RegistrationFlowEvent.NavigateBack -> state.copy(backStack = state.backStack.dropLast(1))
    }
  }

  private fun applyNavigationToScreenEvent(inputState: RegistrationFlowState, event: RegistrationFlowEvent.NavigateToScreen): RegistrationFlowState {
    val state = inputState.copy(backStack = inputState.backStack + event.route)

    return when (event.route) {
      is RegistrationRoute.VerificationCodeEntry -> {
        state.copy(sessionMetadata = event.route.session, sessionE164 = event.route.e164)
      }
      else -> state
    }
  }

  /**
   * Returns the list of permissions to request based on the current API level.
   */
  fun getRequiredPermissions(): List<String> {
    return buildList {
      // Notifications (API 33+)
      if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }

      // Contacts
      add(Manifest.permission.READ_CONTACTS)
      add(Manifest.permission.WRITE_CONTACTS)

      // Storage/Media
      if (Build.VERSION.SDK_INT < 29) {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
      }

      // Phone state
      add(Manifest.permission.READ_PHONE_STATE)
      if (Build.VERSION.SDK_INT >= 26) {
        add(Manifest.permission.READ_PHONE_NUMBERS)
      }
    }
  }

  class Factory(private val repository: RegistrationRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
      return RegistrationViewModel(repository, extras.createSavedStateHandle()) as T
    }
  }
}
