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
 * All of the logic behind a [CodeEntryField]: turning the raw text each digit field reports into a code, moving focus
 * along as the user types, and saying when the code is finished.
 *
 * Meant to be held by the view model of whichever screen shows the field, which feeds it events, mirrors [state] into
 * its own state, and carries out [actions].
 */
class CodeEntryFieldPresenter(
  coroutineScope: CoroutineScope
) : EventDrivenPresenter<CodeEntryFieldEvents>(TAG, coroutineScope) {

  companion object {
    private val TAG = Log.tag(CodeEntryFieldPresenter::class)
  }

  private val _state = MutableStateFlow(CodeEntryFieldState())
  private val _actions = Channel<CodeEntryFieldAction>(Channel.BUFFERED)

  val state: StateFlow<CodeEntryFieldState> = _state.asStateFlow()
  val actions: Flow<CodeEntryFieldAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: CodeEntryFieldEvents) {
    when (event) {
      is CodeEntryFieldEvents.DigitChanged -> {
        applyDigitChanged(event.index, event.value)
      }
    }
  }

  /**
   * Interprets the raw [value] reported by the digit field at [index] and updates the digits and focus accordingly:
   *
   * - an empty [value] is a backspace, deleting a digit and moving focus back
   * - a single digit is recorded and focus advances
   * - multi-character input (e.g. a pasted code) populates every field at once
   *
   * Once every field has a value, the completed code is emitted.
   */
  private suspend fun applyDigitChanged(index: Int, value: String) {
    check(index in _state.value.digits.indices) { "[DigitChanged] Out of bounds index $index." }

    if (value.isEmpty()) {
      deleteDigit(index)
      return
    }

    val currentValue = _state.value.digits[index]
    val remainder = if (currentValue.isNotEmpty()) value.replaceFirst(currentValue, "") else value
    val addedDigits = remainder.filter { it.isDigit() }

    when {
      addedDigits.isEmpty() -> Unit

      addedDigits.length == 1 -> {
        _state.update {
          it.copy(
            digits = it.digits.toMutableList().also { digits -> digits[index] = addedDigits },
            focusedDigitIndex = (index + 1).coerceAtMost(CODE_LENGTH - 1)
          )
        }
        emitCodeIfComplete()
      }

      else -> applyFullCode(addedDigits)
    }
  }

  /**
   * Populates every digit field from a full pasted [code] at once. Multi-character input that isn't a complete code
   * is ignored.
   */
  private suspend fun applyFullCode(code: String) {
    if (code.length != CODE_LENGTH) {
      Log.w(TAG, "[DigitChanged] Ignoring multi-character input containing ${code.length} digits.")
      return
    }

    _state.update {
      it.copy(
        digits = code.map { digit -> digit.toString() },
        focusedDigitIndex = CODE_LENGTH - 1
      )
    }
    emitCodeIfComplete()
  }

  /**
   * Deletes the digit at [index] (or the previous one, if [index] is already empty), shifts any following digits left
   * to fill the gap, and moves focus back.
   */
  private fun deleteDigit(index: Int) {
    val digits = _state.value.digits
    val deleteAt = if (digits[index].isNotEmpty()) index else index - 1
    if (deleteAt < 0) {
      return
    }

    val newDigits = digits.toMutableList().apply {
      for (j in deleteAt until CODE_LENGTH - 1) {
        this[j] = this[j + 1]
      }
      this[CODE_LENGTH - 1] = ""
    }

    _state.update { it.copy(digits = newDigits, focusedDigitIndex = (index - 1).coerceAtLeast(0)) }
  }

  private suspend fun emitCodeIfComplete() {
    val state = _state.value
    if (state.isComplete) {
      _actions.send(CodeEntryFieldAction.CodeEntered(state.code))
    }
  }
}
