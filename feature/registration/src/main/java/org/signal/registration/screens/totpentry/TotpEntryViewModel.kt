/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.totpentry

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.totpentry.TotpEntryState.Companion.CODE_LENGTH
import org.signal.registration.screens.util.navigateBack

/**
 * Drives [TotpEntryScreen]. Interprets raw digit-field input into a six-digit code. The completed code is emitted
 * via [ResultEventBus], then the two-factor screens are popped so the login screen that bounced here can retry.
 */
class TotpEntryViewModel(
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val resultBus: ResultEventBus,
  private val resultKey: String
) : EventDrivenViewModel<TotpEntryScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(TotpEntryViewModel::class)
  }

  private val _state = MutableStateFlow(TotpEntryState())

  val state: StateFlow<TotpEntryState> = _state.asStateFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: TotpEntryScreenEvents) {
    when (event) {
      is TotpEntryScreenEvents.DigitChanged -> {
        applyDigitChanged(event.index, event.value)
      }
      TotpEntryScreenEvents.CancelClicked -> {
        parentEventEmitter.navigateBack()
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
  private fun applyDigitChanged(index: Int, value: String) {
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
  private fun applyFullCode(code: String) {
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

  private fun emitCodeIfComplete() {
    val state = _state.value
    if (state.isComplete) {
      resultBus.sendResult(resultKey, state.code)
      parentEventEmitter(RegistrationFlowEvent.NavigateBackToScreen(RegistrationRoute.SignalLoginCredentialEntry()))
    }
  }
}
