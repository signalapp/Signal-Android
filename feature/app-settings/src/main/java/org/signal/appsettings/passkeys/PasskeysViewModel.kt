/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.passkeys

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives the screen that explains passkeys and lists any that already exist.
 */
class PasskeysViewModel(
  repository: PasskeysRepository
) : EventDrivenViewModel<PasskeysEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(PasskeysViewModel::class)
  }

  private val _state = MutableStateFlow(PasskeysState(passkeys = repository.getPasskeys()))
  private val _actions = Channel<PasskeysAction>(Channel.BUFFERED)

  val state: StateFlow<PasskeysState> = _state.asStateFlow()
  val actions: Flow<PasskeysAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: PasskeysEvent) {
    when (event) {
      PasskeysEvent.NavigateBackClicked -> {
        _actions.send(PasskeysAction.NavigateBack)
      }
      PasskeysEvent.SetUpPasskeyClicked -> {
        _actions.send(PasskeysAction.LaunchPasskeyCreation)
      }
      PasskeysEvent.LearnMoreClicked -> {
        _actions.send(PasskeysAction.OpenLearnMore)
      }
      is PasskeysEvent.RenamePasskeyClicked, is PasskeysEvent.RemovePasskeyClicked -> {
        // Nothing to do yet -- rename and remove haven't been built.
      }
    }
  }
}
