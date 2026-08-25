/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.appsettings.authenticatorsetup.AuthenticatorSetupAction
import org.signal.appsettings.authenticatorsetup.AuthenticatorSetupEvent
import org.signal.appsettings.authenticatorsetup.AuthenticatorSetupState
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives the screen that walks the user through pairing an authenticator app. The setup key is mocked up for now,
 * since there's nothing to fetch it from yet.
 */
class AuthenticatorSetupViewModel(
  repository: AuthenticatorRepository = AuthenticatorRepository()
) : EventDrivenViewModel<AuthenticatorSetupEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(AuthenticatorSetupViewModel::class)
    private const val ACCOUNT_LABEL = "Signal"
  }

  private val _state = MutableStateFlow(AuthenticatorSetupState(setupKey = repository.getSetupKey()))
  private val _actions = Channel<AuthenticatorSetupAction>(Channel.BUFFERED)

  val state: StateFlow<AuthenticatorSetupState> = _state.asStateFlow()
  val actions: Flow<AuthenticatorSetupAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: AuthenticatorSetupEvent) {
    when (event) {
      AuthenticatorSetupEvent.NavigateBackClicked -> {
        _actions.send(AuthenticatorSetupAction.NavigateBack)
      }
      AuthenticatorSetupEvent.OpenAuthenticatorAppClicked -> {
        _actions.send(AuthenticatorSetupAction.LaunchAuthenticatorApp(buildSetupUri(_state.value.setupKey)))
      }
      AuthenticatorSetupEvent.CopyKeyClicked -> {
        _actions.send(AuthenticatorSetupAction.CopyKeyToClipboard(_state.value.setupKey))
        _actions.send(AuthenticatorSetupAction.ShowKeyCopied)
      }
      AuthenticatorSetupEvent.NoAuthenticatorAppFound -> {
        _actions.send(AuthenticatorSetupAction.ShowNoAuthenticatorAppFound)
      }
      AuthenticatorSetupEvent.ContinueClicked -> {
        _actions.send(AuthenticatorSetupAction.NavigateToCodeEntry)
      }
    }
  }

  private fun buildSetupUri(setupKey: String): String {
    return Uri.Builder()
      .scheme("otpauth")
      .authority("totp")
      .appendPath(ACCOUNT_LABEL)
      .appendQueryParameter("secret", setupKey)
      .appendQueryParameter("issuer", ACCOUNT_LABEL)
      .build()
      .toString()
  }
}
