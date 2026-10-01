/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreen
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreenEvents
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsState
import org.thoughtcrime.securesms.components.settings.app.account.signallogin.SignalLoginViewDetailsAction
import org.thoughtcrime.securesms.components.settings.app.account.signallogin.SignalLoginViewDetailsRepository

/**
 * Backs [SignalLoginViewDetailsScreen] within the backup setup flow, where the credentials are only ever shown and
 * saved. Resets belong to account settings, so this only ever emits [SignalLoginViewDetailsAction.Shared].
 */
class MessageBackupsSignalLoginDetailsViewModel(
  repository: SignalLoginViewDetailsRepository = SignalLoginViewDetailsRepository(),
  isPasswordManagerAvailable: Boolean = true
) : EventDrivenViewModel<SignalLoginViewDetailsScreenEvents>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(MessageBackupsSignalLoginDetailsViewModel::class)
  }

  private val _state = MutableStateFlow(
    SignalLoginViewDetailsState(
      accountKey = repository.getAci()?.toString()?.uppercase().orEmpty(),
      recoveryKey = repository.getAccountEntropyPool()?.displayValue.orEmpty(),
      isPasswordManagerAvailable = isPasswordManagerAvailable
    )
  )
  private val _actions = Channel<SignalLoginViewDetailsAction.Shared>(Channel.BUFFERED)

  val state: StateFlow<SignalLoginViewDetailsState> = _state.asStateFlow()
  val actions: Flow<SignalLoginViewDetailsAction.Shared> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: SignalLoginViewDetailsScreenEvents) {
    when (event) {
      SignalLoginViewDetailsScreenEvents.BackClicked -> {
        _actions.send(SignalLoginViewDetailsAction.NavigateBack)
      }
      SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked -> {
        if (_state.value.isPasswordManagerAvailable) {
          _actions.send(SignalLoginViewDetailsAction.LaunchSaveToPasswordManager)
        } else {
          _actions.send(SignalLoginViewDetailsAction.ShowNoPasswordManagerAvailable)
        }
      }
      SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked -> {
        _actions.send(SignalLoginViewDetailsAction.LaunchSaveAsPdf)
      }
      SignalLoginViewDetailsScreenEvents.ResetRecoveryKeyClicked -> {
        Log.w(TAG, "Recovery key resets aren't offered during backup setup.")
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
