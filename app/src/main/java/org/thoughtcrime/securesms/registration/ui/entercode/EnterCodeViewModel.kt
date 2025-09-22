/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.entercode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class EnterCodeViewModel : ViewModel() {
  private val store = MutableStateFlow(EnterCodeState())
  val uiState = store.asLiveData()

  fun resetAllViews() {
    store.update { it.copy(resetRequiredAfterFailure = true) }
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
