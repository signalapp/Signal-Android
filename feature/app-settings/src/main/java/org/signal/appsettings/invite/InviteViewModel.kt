/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.invite

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

class InviteViewModel(
  defaultInviteText: String
) : EventDrivenViewModel<InviteEvent>(TAG, shouldLogEvents = true) {
  companion object {
    private val TAG = Log.tag(InviteViewModel::class)
  }

  private val _state = MutableStateFlow(InviteState(defaultInviteText))
  private val _actions = Channel<InviteAction>(Channel.BUFFERED)

  val state: StateFlow<InviteState> = _state.asStateFlow()
  val actions: Flow<InviteAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: InviteEvent) {
    when (event) {
      is InviteEvent.InviteTextChanged -> {
        _state.update { it.copy(inviteText = event.updatedText) }
      }

      is InviteEvent.ShareClicked -> {
        _actions.send(InviteAction.ShareInvite(state.value.inviteText))
      }

      is InviteEvent.NavigateBackClicked -> {
        _actions.send(InviteAction.NavigateBack)
      }
    }
  }
}
