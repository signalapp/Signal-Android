/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.appsettings.totpsetup.TotpSetupAction
import org.signal.appsettings.totpsetup.TotpSetupEvent
import org.signal.appsettings.totpsetup.TotpSetupState
import org.signal.appsettings.totpsetup.TotpSetupState.Dialog
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TotpSetupViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  private val repository: TotpRepository = TotpRepository(),
  private val accountName: String = accountNameFor(LocalDateTime.now())
) : EventDrivenViewModel<TotpSetupEvent>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(TotpSetupViewModel::class)

    @VisibleForTesting
    fun accountNameFor(time: LocalDateTime): String = time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
  }

  private val _state = MutableStateFlow(TotpSetupState())
  private val _actions = Channel<TotpSetupAction>(Channel.BUFFERED)

  val state: StateFlow<TotpSetupState> = _state.asStateFlow()
  val actions: Flow<TotpSetupAction> = _actions.receiveAsFlow()

  /** The URI and clipboard forms of the key, which differ from the grouped form the screen shows. */
  private var setupUri: String = ""
  private var clipboardKey: String = ""

  init {
    viewModelScope.launch { beginSetup() }
  }

  override suspend fun processEvent(event: TotpSetupEvent) {
    when (event) {
      TotpSetupEvent.NavigateBackClicked -> {
        parentEventEmitter.navigateBack()
      }
      TotpSetupEvent.OpenTotpAppClicked -> {
        _actions.send(TotpSetupAction.LaunchTotpApp(setupUri))
      }
      TotpSetupEvent.CopyKeyClicked -> {
        _actions.send(TotpSetupAction.CopyKeyToClipboard(clipboardKey))
        _state.update { it.copy(snackbar = TotpSetupState.Snackbar.KeyCopied) }
      }
      TotpSetupEvent.NoTotpAppFound -> {
        _state.update { it.copy(snackbar = TotpSetupState.Snackbar.NoTotpAppFound) }
      }
      TotpSetupEvent.ContinueClicked -> {
        parentEventEmitter.navigateTo(AppSettingsRoute.AccountRoute.TotpCodeEntry)
      }
      TotpSetupEvent.DialogDismissed -> {
        _state.update { it.copy(dialog = Dialog.None) }
        parentEventEmitter.navigateBack()
      }
      TotpSetupEvent.SnackbarDismissed -> {
        _state.update { it.copy(snackbar = TotpSetupState.Snackbar.None) }
      }
    }
  }

  private suspend fun beginSetup() {
    when (val result = repository.beginSetup(accountName)) {
      is TotpRepository.BeginSetupResult.Success -> {
        setupUri = result.setupUri
        clipboardKey = result.clipboardKey
        _state.update { it.copy(setupKey = result.displayKey, loading = false) }
      }
      TotpRepository.BeginSetupResult.TooManyApps -> {
        Log.w(TAG, "The account already has as many authenticator apps as it's allowed.")
        _state.update { it.copy(loading = false, dialog = Dialog.MaxAppsReached(repository.getMaxApps())) }
      }
      TotpRepository.BeginSetupResult.NetworkFailure -> {
        Log.w(TAG, "Couldn't reach the service to start setup.")
        _state.update { it.copy(loading = false, dialog = Dialog.NetworkFailure) }
      }
    }
  }
}
