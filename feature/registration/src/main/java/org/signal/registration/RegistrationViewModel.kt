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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

  init {
    _state.value = _state.value.copy(isRestoringNavigationState = true)
    viewModelScope.launch {
      val restored = repository.restoreFlowState()
      if (restored != null) {
        Log.i(TAG, "[init] Restored flow state from disk. Backstack size: ${restored.backStack.size}, hasSession: ${restored.sessionMetadata != null}")
        _state.value = validateRestoredState(restored).copy(isRestoringNavigationState = false)
      } else {
        _state.value = _state.value.copy(
          preExistingRegistrationData = repository.getPreExistingRegistrationData(),
          isRestoringNavigationState = false
        )
      }
    }
  }

  fun onEvent(event: RegistrationFlowEvent) {
    Log.d(TAG, "[Event] $event")
    _state.value = applyEvent(_state.value, event)

    viewModelScope.launch(Dispatchers.IO) {
      persistFlowState(event)
    }
  }

  fun applyEvent(state: RegistrationFlowState, event: RegistrationFlowEvent): RegistrationFlowState {
    return when (event) {
      is RegistrationFlowEvent.ResetState -> RegistrationFlowState(isRestoringNavigationState = false)
      is RegistrationFlowEvent.SessionUpdated -> state.copy(sessionMetadata = event.session)
      is RegistrationFlowEvent.E164Chosen -> state.copy(sessionE164 = event.e164)
      is RegistrationFlowEvent.Registered -> state.copy(accountEntropyPool = event.accountEntropyPool)
      is RegistrationFlowEvent.MasterKeyRestoredFromSvr -> state.copy(temporaryMasterKey = event.masterKey)
      is RegistrationFlowEvent.NavigateToScreen -> applyNavigationToScreenEvent(state, event)
      is RegistrationFlowEvent.NavigateBack -> state.copy(backStack = state.backStack.dropLast(1))
      is RegistrationFlowEvent.RecoveryPasswordInvalid -> state.copy(doNotAttemptRecoveryPassword = true)
    }
  }

  private fun applyNavigationToScreenEvent(inputState: RegistrationFlowState, event: RegistrationFlowEvent.NavigateToScreen): RegistrationFlowState {
    return inputState.copy(backStack = inputState.backStack + event.route)
  }

  /**
   * Validates a restored flow state by checking if the session is still valid.
   *
   * - If the session is still valid, updates session metadata with fresh data.
   * - If the session is expired and the user is already registered, nulls out the session
   *   (post-registration screens like PinCreate don't need a session).
   * - If the session is expired and the user is NOT registered, resets the backstack to
   *   PhoneNumberEntry with the phone number pre-filled so the user can re-submit.
   */
  private suspend fun validateRestoredState(state: RegistrationFlowState): RegistrationFlowState {
    val sessionMetadata = state.sessionMetadata ?: return state

    val freshSession = repository.validateSession(sessionMetadata.id)
    if (freshSession != null) {
      Log.i(TAG, "[validateRestoredState] Session still valid.")
      return state.copy(sessionMetadata = freshSession)
    }

    Log.i(TAG, "[validateRestoredState] Session expired/invalid.")

    if (repository.isRegistered()) {
      Log.i(TAG, "[validateRestoredState] User is registered, proceeding without session.")
      return state.copy(sessionMetadata = null)
    }

    Log.i(TAG, "[validateRestoredState] User is NOT registered, resetting to PhoneNumberEntry.")
    return state.copy(
      backStack = listOf(
        RegistrationRoute.Welcome,
        RegistrationRoute.Permissions(nextRoute = RegistrationRoute.PhoneNumberEntry),
        RegistrationRoute.PhoneNumberEntry
      ),
      sessionMetadata = null
    )
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

  private suspend fun persistFlowState(event: RegistrationFlowEvent) {
    when (event) {
      is RegistrationFlowEvent.ResetState -> repository.clearFlowState()
      is RegistrationFlowEvent.NavigateToScreen -> {
        if (event.route is RegistrationRoute.FullyComplete) {
          repository.clearFlowState()
        } else {
          repository.saveFlowState(_state.value)
        }
      }
      is RegistrationFlowEvent.NavigateBack,
      is RegistrationFlowEvent.SessionUpdated,
      is RegistrationFlowEvent.E164Chosen,
      is RegistrationFlowEvent.RecoveryPasswordInvalid -> repository.saveFlowState(_state.value)

      // No need to persist anything new, fields accounted for in proto already
      is RegistrationFlowEvent.Registered,
      is RegistrationFlowEvent.MasterKeyRestoredFromSvr -> { }
    }
  }

  class Factory(private val repository: RegistrationRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
      return RegistrationViewModel(repository, extras.createSavedStateHandle()) as T
    }
  }
}
