/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.reregisterwithpin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class ReRegisterWithPinViewModel : ViewModel() {
  private val store = MutableStateFlow(ReRegisterWithPinState())

  val uiState = store.asLiveData()

  val isLocalVerification: Boolean
    get() = store.value.isLocalVerification

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
