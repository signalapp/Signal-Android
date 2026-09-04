/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.signallogin

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreenEvents
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsState

/**
 * Drives the screen that shows the user the account and recovery keys that make up their Signal Login, reached from
 * account settings.
 */
class SignalLoginViewDetailsViewModel(
  repository: SignalLoginViewDetailsRepository = SignalLoginViewDetailsRepository()
) : EventDrivenViewModel<SignalLoginViewDetailsScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(SignalLoginViewDetailsViewModel::class)
  }

  private val _state = MutableStateFlow(
    SignalLoginViewDetailsState(
      accountKey = repository.getAci()?.toString()?.uppercase().orEmpty(),
      recoveryKey = repository.getAccountEntropyPool()?.displayValue.orEmpty()
    )
  )
  private val _actions = Channel<SignalLoginViewDetailsAction>(Channel.BUFFERED)

  val state: StateFlow<SignalLoginViewDetailsState> = _state.asStateFlow()
  val actions: Flow<SignalLoginViewDetailsAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: SignalLoginViewDetailsScreenEvents) {
    when (event) {
      SignalLoginViewDetailsScreenEvents.BackClicked -> {
        _actions.send(SignalLoginViewDetailsAction.NavigateBack)
      }
      SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked -> {
        _actions.send(SignalLoginViewDetailsAction.LaunchSaveToPasswordManager)
      }
      SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked -> {
        _actions.send(SignalLoginViewDetailsAction.LaunchSaveAsPdf)
      }
      is SignalLoginViewDetailsScreenEvents.CopyAccountIdClicked -> {
        _actions.send(SignalLoginViewDetailsAction.CopyTextToClipboard(event.aci))
      }
      is SignalLoginViewDetailsScreenEvents.CopyRecoveryKeyClicked -> {
        _actions.send(SignalLoginViewDetailsAction.CopyTextToClipboard(event.aep))
      }
    }
  }
}
