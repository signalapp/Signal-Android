/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogindetails

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
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.screens.util.navigateBack
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreen
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreenEvents
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsState

/**
 * View model backing [SignalLoginViewDetailsScreen] within the registration flow.
 */
class RegistrationSignalLoginDetailsViewModel(
  parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<SignalLoginViewDetailsScreenEvents>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(RegistrationSignalLoginDetailsViewModel::class)
  }

  private val _state = MutableStateFlow(
    SignalLoginViewDetailsState(
      accountKey = parentState.value.aci?.toString()?.uppercase().orEmpty(),
      recoveryKey = parentState.value.accountEntropyPool?.displayValue.orEmpty(),
      showSaveToPasswordManagerButton = false
    )
  )
  val state: StateFlow<SignalLoginViewDetailsState> = _state.asStateFlow()

  private val _actions = Channel<RegistrationSignalLoginDetailsAction>(Channel.BUFFERED)
  val actions: Flow<RegistrationSignalLoginDetailsAction> = _actions.receiveAsFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: SignalLoginViewDetailsScreenEvents) {
    applyEvent(event, parentEventEmitter)
  }

  @VisibleForTesting
  fun applyEvent(event: SignalLoginViewDetailsScreenEvents, parentEventEmitter: (RegistrationFlowEvent) -> Unit) {
    when (event) {
      is SignalLoginViewDetailsScreenEvents.BackClicked -> {
        parentEventEmitter.navigateBack()
      }

      is SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked,
      is SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerConfirmed,
      is SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerConfirmationDismissed -> {
        Log.w(TAG, "Saving to the password manager isn't offered during registration.")
      }

      is SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked -> {
        _state.update { it.copy(doNotShareWarningAction = SignalLoginViewDetailsState.DoNotShareWarningAction.SAVE_AS_PDF) }
      }

      is SignalLoginViewDetailsScreenEvents.ResetRecoveryKeyClicked -> {
        Log.w(TAG, "Recovery key resets aren't offered during registration.")
      }

      is SignalLoginViewDetailsScreenEvents.CopyAccountIdClicked -> {
        _state.update { it.copy(doNotShareWarningAction = SignalLoginViewDetailsState.DoNotShareWarningAction.COPY_ACCOUNT_ID) }
      }

      is SignalLoginViewDetailsScreenEvents.CopyRecoveryKeyClicked -> {
        _state.update { it.copy(doNotShareWarningAction = SignalLoginViewDetailsState.DoNotShareWarningAction.COPY_RECOVERY_KEY) }
      }

      is SignalLoginViewDetailsScreenEvents.DoNotShareWarningGotItClicked -> {
        val state = _state.value
        _state.update { it.copy(doNotShareWarningAction = null) }
        when (state.doNotShareWarningAction) {
          SignalLoginViewDetailsState.DoNotShareWarningAction.COPY_ACCOUNT_ID -> {
            _actions.trySend(RegistrationSignalLoginDetailsAction.CopyTextToClipboard(state.accountKey))
          }
          SignalLoginViewDetailsState.DoNotShareWarningAction.COPY_RECOVERY_KEY -> {
            _actions.trySend(RegistrationSignalLoginDetailsAction.CopyTextToClipboard(state.recoveryKey))
          }
          SignalLoginViewDetailsState.DoNotShareWarningAction.SAVE_AS_PDF -> {
            _actions.trySend(RegistrationSignalLoginDetailsAction.LaunchSaveAsPdf)
          }
          null -> {
            Log.w(TAG, "Do-not-share warning acknowledged with nothing waiting behind it. Ignoring.")
          }
        }
      }

      is SignalLoginViewDetailsScreenEvents.DoNotShareWarningLearnMoreClicked -> {
        _state.update { it.copy(doNotShareWarningAction = null) }
        _actions.trySend(RegistrationSignalLoginDetailsAction.OpenDoNotShareLearnMoreArticle)
      }

      is SignalLoginViewDetailsScreenEvents.DoNotShareWarningDismissed -> {
        _state.update { it.copy(doNotShareWarningAction = null) }
      }
    }
  }
}
