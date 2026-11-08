/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.codeentryfield

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenPresenter
import org.signal.core.util.logging.Log
import org.signal.uicomponents.codeentryfield.CodeEntryFieldState.Companion.CODE_LENGTH

/**
 * All of the logic behind a [CodeEntryField]: tracking the code the field reports, letting the host replace or clear
 * it, and saying when the code is finished.
 *
 * Meant to be held by the view model of whichever screen shows the field, which feeds it events, mirrors [state] into
 * its own state, and carries out [actions].
 */
class CodeEntryFieldPresenter(
  coroutineScope: CoroutineScope
) : EventDrivenPresenter<CodeEntryFieldEvents>(TAG, coroutineScope, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(CodeEntryFieldPresenter::class)
  }

  private val _state = MutableStateFlow(CodeEntryFieldState())
  private val _actions = Channel<CodeEntryFieldAction>(Channel.BUFFERED)

  val state: StateFlow<CodeEntryFieldState> = _state.asStateFlow()
  val actions: Flow<CodeEntryFieldAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: CodeEntryFieldEvents) {
    when (event) {
      is CodeEntryFieldEvents.CodeChanged -> applyCodeChanged(event.code)
      is CodeEntryFieldEvents.OverwriteApplied -> _state.update { it.copy(pendingOverwrite = null) }
      is CodeEntryFieldEvents.SetCode -> applySetCode(event.code)
      is CodeEntryFieldEvents.Clear -> applySetCode("")
    }
  }

  /**
   * Records what the field now holds. While an overwrite is pending, the field's contents are about to be replaced, so
   * anything it reports in the meantime is stale and ignored.
   */
  private suspend fun applyCodeChanged(code: String) {
    val state = _state.value
    if (state.pendingOverwrite != null || code == state.code) {
      return
    }

    _state.update { it.copy(code = code) }
    emitCodeIfComplete()
  }

  private suspend fun applySetCode(code: String) {
    if (code.length > CODE_LENGTH || !code.all { it.isDigit() }) {
      Log.w(TAG, "[SetCode] Ignoring a code that isn't up to $CODE_LENGTH digits. Length: ${code.length}")
      return
    }

    _state.update { it.copy(code = code, pendingOverwrite = code) }
    emitCodeIfComplete()
  }

  private suspend fun emitCodeIfComplete() {
    val state = _state.value
    if (state.isComplete) {
      _actions.send(CodeEntryFieldAction.CodeEntered(state.code))
    }
  }
}
