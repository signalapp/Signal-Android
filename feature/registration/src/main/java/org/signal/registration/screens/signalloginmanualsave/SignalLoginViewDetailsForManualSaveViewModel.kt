/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginmanualsave

import androidx.annotation.VisibleForTesting
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
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo

/**
 * View model for [SignalLoginViewDetailsForManualSaveScreen].
 */
class SignalLoginViewDetailsForManualSaveViewModel(
  parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<SignalLoginViewDetailsForManualSaveScreenEvents>(TAG, shouldLogEvents = false) {

  companion object {
    private val TAG = Log.tag(SignalLoginViewDetailsForManualSaveViewModel::class)
  }

  private val _state = MutableStateFlow(SignalLoginViewDetailsForManualSaveState())
  val state: StateFlow<SignalLoginViewDetailsForManualSaveState> = _state.asStateFlow()

  private val _actions = Channel<SignalLoginViewDetailsForManualSaveScreenActions>(Channel.BUFFERED)
  val actions: Flow<SignalLoginViewDetailsForManualSaveScreenActions> = _actions.receiveAsFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    parentState
      .onEach { onEvent(SignalLoginViewDetailsForManualSaveScreenEvents.ParentStateChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: SignalLoginViewDetailsForManualSaveScreenEvents) {
    applyEvent(_state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  fun applyEvent(
    state: SignalLoginViewDetailsForManualSaveState,
    event: SignalLoginViewDetailsForManualSaveScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (SignalLoginViewDetailsForManualSaveState) -> Unit
  ) {
    when (event) {
      is SignalLoginViewDetailsForManualSaveScreenEvents.ParentStateChanged -> {
        stateEmitter(
          state.copy(
            accountId = event.parentState.aci?.toString()?.uppercase().orEmpty(),
            recoveryKey = event.parentState.accountEntropyPool?.displayValue.orEmpty()
          )
        )
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.BackClicked -> {
        parentEventEmitter.navigateBack()
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.CopyAccountIdClicked -> {
        stateEmitter(state.copy(doNotShareWarningAction = SignalLoginViewDetailsForManualSaveState.DoNotShareWarningAction.COPY_ACCOUNT_ID))
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.CopyRecoveryKeyClicked -> {
        stateEmitter(state.copy(doNotShareWarningAction = SignalLoginViewDetailsForManualSaveState.DoNotShareWarningAction.COPY_RECOVERY_KEY))
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.SaveAsPdfClicked -> {
        stateEmitter(state.copy(doNotShareWarningAction = SignalLoginViewDetailsForManualSaveState.DoNotShareWarningAction.SAVE_AS_PDF))
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.DoNotShareWarningGotItClicked -> {
        stateEmitter(state.copy(doNotShareWarningAction = null))
        when (state.doNotShareWarningAction) {
          SignalLoginViewDetailsForManualSaveState.DoNotShareWarningAction.COPY_ACCOUNT_ID -> {
            _actions.trySend(SignalLoginViewDetailsForManualSaveScreenActions.CopyTextToClipboard(state.accountId))
          }
          SignalLoginViewDetailsForManualSaveState.DoNotShareWarningAction.COPY_RECOVERY_KEY -> {
            _actions.trySend(SignalLoginViewDetailsForManualSaveScreenActions.CopyTextToClipboard(state.recoveryKey))
          }
          SignalLoginViewDetailsForManualSaveState.DoNotShareWarningAction.SAVE_AS_PDF -> {
            _actions.trySend(SignalLoginViewDetailsForManualSaveScreenActions.LaunchSaveAsPdf)
          }
          null -> {
            Log.w(TAG, "Do-not-share warning acknowledged with nothing waiting behind it. Ignoring.")
          }
        }
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.DoNotShareWarningLearnMoreClicked -> {
        stateEmitter(state.copy(doNotShareWarningAction = null))
        _actions.trySend(SignalLoginViewDetailsForManualSaveScreenActions.OpenDoNotShareLearnMoreArticle)
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.DoNotShareWarningDismissed -> {
        stateEmitter(state.copy(doNotShareWarningAction = null))
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.ContinueClicked -> {
        stateEmitter(state.copy(showConfirmSavedSheet = true))
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.ConfirmSavedContinueClicked -> {
        stateEmitter(state.copy(showConfirmSavedSheet = false))
        parentEventEmitter.navigateTo(RegistrationRoute.SignalLoginManualSaveConfirmation)
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.ShowLoginInfoAgainClicked,
      is SignalLoginViewDetailsForManualSaveScreenEvents.ConfirmSavedSheetDismissed -> {
        stateEmitter(state.copy(showConfirmSavedSheet = false))
      }
    }
  }
}
