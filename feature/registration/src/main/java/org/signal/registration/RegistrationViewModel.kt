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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.core.util.logging.Log
import org.signal.registration.screens.restoreselection.RegisteredState
import kotlin.reflect.KClass

/**
 * ViewModel shared across the registration flow.
 * Manages state and logic for registration screens.
 */
class RegistrationViewModel(
  private val repository: RegistrationRepository,
  private val savedStateHandle: SavedStateHandle,
  startDestination: RegistrationRoute? = null,
  private val startFresh: Boolean = false
) : EventDrivenViewModel<RegistrationFlowEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(RegistrationViewModel::class)

    /**
     * Marks that a start-fresh reset has already been performed for this ViewModel. Persisted in the
     * [SavedStateHandle] so that a process death mid-flow (which re-delivers the start-fresh intent) does not
     * wipe the fresh in-progress data the user has since built up.
     */
    private const val RESET_PERFORMED_KEY = "start_fresh_reset_performed"
  }

  private var _state: MutableStateFlow<RegistrationFlowState> = savedStateHandle.getMutableStateFlow(
    "registration_state",
    initialValue = RegistrationFlowState(backStack = listOf(startDestination ?: RegistrationRoute.Welcome))
  )
  val state: StateFlow<RegistrationFlowState> = _state.asStateFlow()

  private val finishChannel = Channel<Unit>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val finishRequests: Flow<Unit> = finishChannel.receiveAsFlow()

  val resultBus = ResultEventBus()

  init {
    if (startDestination != null) {
      _state.value = _state.value.copy(isRestoringNavigationState = false)
    } else {
      _state.value = _state.value.copy(isRestoringNavigationState = true)
      viewModelScope.launch {
        if (startFresh && savedStateHandle.get<Boolean>(RESET_PERFORMED_KEY) != true) {
          Log.i(TAG, "[init] Start-fresh requested. Clearing any persisted in-progress registration data so the user starts fresh.")
          repository.clearInProgressRegistrationData()
          savedStateHandle[RESET_PERFORMED_KEY] = true
          _state.value = RegistrationFlowState(
            preExistingRegistrationData = repository.getPreExistingRegistrationData(),
            isRestoringNavigationState = false
          )
          return@launch
        }

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
  }

  override suspend fun processEvent(event: RegistrationFlowEvent) {
    _state.value = applyEvent(_state.value, event)
    persistFlowState(event)
  }

  suspend fun applyEvent(state: RegistrationFlowState, event: RegistrationFlowEvent): RegistrationFlowState {
    return when (event) {
      is RegistrationFlowEvent.ResetState -> RegistrationFlowState(isRestoringNavigationState = false)
      is RegistrationFlowEvent.SessionUpdated -> state.copy(sessionMetadata = event.session)
      is RegistrationFlowEvent.E164Chosen -> state.copy(sessionE164 = event.e164)
      is RegistrationFlowEvent.Registered -> state.copy(accountEntropyPool = event.accountEntropyPool, storageCapable = event.storageCapable)
      is RegistrationFlowEvent.MasterKeyRestoredFromSvr -> state.copy(temporaryMasterKey = event.masterKey)
      is RegistrationFlowEvent.NavigateToScreen -> applyNavigationToScreenEvent(state, event)
      is RegistrationFlowEvent.NavigateBackToScreen -> applyNavigateBackToScreenEvent(state, event)
      is RegistrationFlowEvent.NavigateBack -> {
        if (state.backStack.size > 1) {
          state.copy(backStack = state.backStack.dropLast(1))
        } else {
          finishChannel.trySend(Unit)
          state
        }
      }
      is RegistrationFlowEvent.RecoveryPasswordInvalid -> state.copy(doNotAttemptRecoveryPassword = true)
      is RegistrationFlowEvent.PendingRestoreOptionSelected -> state.copy(pendingRestoreOption = event.option)
      is RegistrationFlowEvent.RestoreMethodTokenReceived -> state.copy(restoreMethodToken = event.token)
      is RegistrationFlowEvent.UserSuppliedAepSubmitted -> state.copy(unverifiedRestoredAep = event.aep)
      is RegistrationFlowEvent.UserSuppliedAepVerified -> {
        repository.saveVerifiedUserSuppliedAep(event.aep)
        state.copy(accountEntropyPool = event.aep, unverifiedRestoredAep = null)
      }
      is RegistrationFlowEvent.RegistrationComplete -> {
        repository.commitFinalRegistrationData()
        val completeNavEvent = RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.FullyComplete)
        applyNavigationToScreenEvent(state, completeNavEvent)
      }
    }
  }

  private fun applyNavigationToScreenEvent(inputState: RegistrationFlowState, event: RegistrationFlowEvent.NavigateToScreen): RegistrationFlowState {
    val backStack = when {
      event.route.clearsBackStack() -> listOf(event.route)
      event.popCurrent -> inputState.backStack.dropLast(1) + event.route
      else -> inputState.backStack + event.route
    }
    return inputState.copy(backStack = backStack)
  }

  /**
   * Whether navigating to this route should clear the back stack, preventing the user from navigating any further back.
   * This is used for "point of no return" screens: once the user has registered or restored data, they should not be
   * able to return to earlier screens in the flow.
   */
  private fun RegistrationRoute.clearsBackStack(): Boolean {
    return when (this) {
      is RegistrationRoute.Welcome,
      is RegistrationRoute.PinCreate,
      is RegistrationRoute.PinEntryForSvrRestore,
      is RegistrationRoute.RemoteRestore -> true
      is RegistrationRoute.ArchiveRestoreSelection -> this.registeredState != RegisteredState.NotRegistered
      else -> false
    }
  }

  private fun applyNavigateBackToScreenEvent(inputState: RegistrationFlowState, event: RegistrationFlowEvent.NavigateBackToScreen): RegistrationFlowState {
    val index = inputState.backStack.indexOfLast { it == event.route }
    return if (index >= 0) {
      inputState.copy(backStack = inputState.backStack.take(index + 1))
    } else {
      inputState.copy(backStack = inputState.backStack.dropLast(1) + event.route)
    }
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

  fun getRequiredLinkedDevicePermission(): String? {
    return if (Build.VERSION.SDK_INT >= 33) {
      Manifest.permission.POST_NOTIFICATIONS
    } else {
      null
    }
  }

  private suspend fun persistFlowState(event: RegistrationFlowEvent) {
    when (event) {
      is RegistrationFlowEvent.ResetState -> repository.clearFlowState()
      is RegistrationFlowEvent.NavigateToScreen -> {
        if (event.route is RegistrationRoute.FullyComplete) {
          repository.clearInProgressRegistrationData()
        } else {
          repository.saveFlowState(_state.value)
        }
      }
      is RegistrationFlowEvent.NavigateBack,
      is RegistrationFlowEvent.NavigateBackToScreen,
      is RegistrationFlowEvent.SessionUpdated,
      is RegistrationFlowEvent.E164Chosen,
      is RegistrationFlowEvent.RecoveryPasswordInvalid,
      is RegistrationFlowEvent.PendingRestoreOptionSelected,
      is RegistrationFlowEvent.RestoreMethodTokenReceived,
      is RegistrationFlowEvent.UserSuppliedAepSubmitted,
      is RegistrationFlowEvent.UserSuppliedAepVerified -> repository.saveFlowState(_state.value)
      is RegistrationFlowEvent.RegistrationComplete -> repository.clearInProgressRegistrationData()

      // No need to persist anything new, fields accounted for in proto already
      is RegistrationFlowEvent.Registered,
      is RegistrationFlowEvent.MasterKeyRestoredFromSvr -> { }
    }
  }

  class Factory(private val repository: RegistrationRepository, private val startDestination: RegistrationRoute? = null, private val startFresh: Boolean = false) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
      return RegistrationViewModel(repository, extras.createSavedStateHandle(), startDestination, startFresh) as T
    }
  }
}
