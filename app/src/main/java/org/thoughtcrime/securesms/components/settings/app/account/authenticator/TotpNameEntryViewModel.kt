/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.totp.TotpApp
import org.signal.appsettings.totpnameentry.TotpNameEntryAction
import org.signal.appsettings.totpnameentry.TotpNameEntryEvent
import org.signal.appsettings.totpnameentry.TotpNameEntryState
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.BreakIteratorCompat
import org.signal.core.util.StringUtil
import org.signal.core.util.logging.Log

/**
 * Handles both naming and renaming TOTP apps.
 */
class TotpNameEntryViewModel(
  private val appId: Long,
  private val renamedApp: TotpApp? = null,
  private val repository: TotpRepository = TotpRepository()
) : EventDrivenViewModel<TotpNameEntryEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(TotpNameEntryViewModel::class)
  }

  private val breakIterator = BreakIteratorCompat.getInstance()

  private val _state = MutableStateFlow(
    TotpNameEntryState(
      renaming = renamedApp != null,
      name = renamedApp?.name?.trimNameToLengthLimits() ?: ""
    )
  )
  private val _actions = Channel<TotpNameEntryAction>(Channel.BUFFERED)

  val state: StateFlow<TotpNameEntryState> = _state.asStateFlow()
  val actions: Flow<TotpNameEntryAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: TotpNameEntryEvent) {
    when (event) {
      TotpNameEntryEvent.NavigateBackClicked -> {
        _actions.send(TotpNameEntryAction.NavigateBack)
      }
      is TotpNameEntryEvent.NameChanged -> {
        _state.update { it.copy(name = event.name.trimNameToLengthLimits()) }
      }
      TotpNameEntryEvent.NextClicked -> {
        if (!_state.value.canSubmit) {
          return
        }

        val name = _state.value.name.trim()
        _state.update { it.copy(submitting = true) }

        val result = if (renamedApp != null) {
          repository.renameTotpApp(renamedApp, name)
        } else {
          repository.nameNewTotpApp(appId, name)
        }

        when (result) {
          TotpRepository.UpdateResult.Success -> {
            _actions.send(if (renamedApp != null) TotpNameEntryAction.ShowTotpAppRenamed else TotpNameEntryAction.ShowTotpAppSetUp)
            _actions.send(TotpNameEntryAction.NavigateToAccountSettings)
          }
          TotpRepository.UpdateResult.AppNotFound -> {
            Log.w(TAG, "Asked to name an app the service doesn't have. Going back to account settings rather than stranding the user here.")
            _actions.send(TotpNameEntryAction.NavigateToAccountSettings)
          }
          TotpRepository.UpdateResult.NetworkFailure -> {
            _state.update { it.copy(submitting = false) }
            _actions.send(TotpNameEntryAction.ShowNameNotSaved)
          }
        }
      }
    }
  }

  /** Keeps totp name within length limits */
  private fun String.trimNameToLengthLimits(): String {
    val input = this
    val graphemeTruncated = breakIterator
      .apply { setText(input) }
      .take(TotpRepository.MAX_NAME_LENGTH_GRAPHEMES)
      .toString()

    return StringUtil.trimToFit(graphemeTruncated, TotpRepository.MAX_NAME_LENGTH_BYTES)
  }
}
