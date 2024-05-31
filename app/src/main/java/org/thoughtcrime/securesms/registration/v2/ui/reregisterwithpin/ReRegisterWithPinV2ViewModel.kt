/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.ui.reregisterwithpin

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.logging.Log

class ReRegisterWithPinV2ViewModel : ViewModel() {
  companion object {
    private val TAG = Log.tag(ReRegisterWithPinV2ViewModel::class.java)
  }

  private val store = MutableStateFlow(ReRegisterWithPinV2State())

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
