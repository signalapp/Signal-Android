/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.controls

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

/**
 * Provides a view model communicating with the Controls and Info view, [CallInfoView].
 */
class ControlsAndInfoViewModel : ViewModel() {
  private val _state = mutableStateOf(ControlAndInfoState())
  val state: State<ControlAndInfoState> = _state

  fun resetScrollState() {
    _state.value = _state.value.copy(resetScrollState = System.currentTimeMillis())
  }
}
