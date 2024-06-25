/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.reregisterwithpin

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.logging.Log

class ReRegisterWithPinViewModel : ViewModel() {
  companion object {
    private val TAG = Log.tag(ReRegisterWithPinViewModel::class.java)
  }

  private val store = MutableStateFlow(ReRegisterWithPinState())

  val isLocalVerification: Boolean
    get() = store.value.isLocalVerification
  val hasIncorrectGuess: Boolean
    get() = store.value.hasIncorrectGuess

  fun markAsRemoteVerification() {
    store.update {
      it.copy(isLocalVerification = false)
    }
  }

  fun markIncorrectGuess() {
    store.update {
      it.copy(hasIncorrectGuess = true)
    }
  }
}
