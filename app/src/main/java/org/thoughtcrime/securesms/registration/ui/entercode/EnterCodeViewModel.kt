/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.entercode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.thoughtcrime.securesms.util.CodeEntryController

class EnterCodeViewModel : ViewModel() {
  private val store = MutableStateFlow(EnterCodeState())
  val uiState = store.asLiveData()

  // Use composition for code entry logic
  private val codeEntryController = CodeEntryController()
  val codeState: StateFlow<List<String>> get() = codeEntryController.codeState
  fun setDigit(idx: Int, digit: String) = codeEntryController.setDigit(idx, digit)
  fun clearDigit(idx: Int) = codeEntryController.clearDigit(idx)
  fun appendDigit(digit: String) = codeEntryController.appendDigit(digit)
  fun deleteLastDigit() = codeEntryController.deleteLastDigit()
  fun clearAllDigits() = codeEntryController.clearAllDigits()
  fun autofillCode(code: String) = codeEntryController.autofillCode(code)

  fun resetAllViews() {
    store.update { it.copy(resetRequiredAfterFailure = true) }
    clearAllDigits()
  }

  fun allViewsResetCompleted() {
    store.update {
      it.copy(
        resetRequiredAfterFailure = false,
        showKeyboard = false
      )
    }
  }

  fun showKeyboard() {
    store.update { it.copy(showKeyboard = true) }
  }

  fun keyboardShown() {
    store.update { it.copy(showKeyboard = false) }
  }
}
