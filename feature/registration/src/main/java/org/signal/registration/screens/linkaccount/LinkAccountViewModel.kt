/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.linkaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.EventDrivenViewModel
import org.signal.registration.screens.util.navigateTo

class LinkAccountViewModel(
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<LinkAccountScreenEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(LinkAccountViewModel::class)
  }

  private val _state = MutableStateFlow(LinkAccountScreenState())
  val state: StateFlow<LinkAccountScreenState> = _state.asStateFlow()

  // TODO [regv5] - load qr code data

  override suspend fun processEvent(event: LinkAccountScreenEvent) {
    when (event) {
      LinkAccountScreenEvent.GetHelpClick -> error("This event is handled in the nav-entry.")
      LinkAccountScreenEvent.CreateAccountClick -> parentEventEmitter.navigateTo(RegistrationRoute.Permissions(nextRoute = RegistrationRoute.PhoneNumberEntry))
      LinkAccountScreenEvent.DisplayOverlayClick -> _state.update { it.copy(displayQrOverlay = true) }
      LinkAccountScreenEvent.HideOverlayClick -> _state.update { it.copy(displayQrOverlay = false) }
    }
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return LinkAccountViewModel(repository, parentState, parentEventEmitter) as T
    }
  }
}
