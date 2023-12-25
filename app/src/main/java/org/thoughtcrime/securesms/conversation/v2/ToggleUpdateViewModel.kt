/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ToggleUpdateViewModel : ViewModel() {

  private val _state = MutableSharedFlow<Unit>(replay = 1)
  val state = _state.asSharedFlow()

  fun update() = viewModelScope.launch {
    _state.emit(Unit)
  }
}
