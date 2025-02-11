/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * Collects and manages state objects for manipulating the call screen UI programatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
class CallScreenController private constructor(
  val scaffoldState: BottomSheetScaffoldState,
  val onControlsToggled: (Boolean) -> Unit
) {

  var restartTimerRequests by mutableLongStateOf(0L)

  suspend fun handleEvent(event: Event) {
    when (event) {
      Event.SWITCH_TO_SPEAKER_VIEW -> {} // TODO [calling-v2]
      Event.DISMISS_AUDIO_PICKER -> {} // TODO [calling-v2]
      Event.TOGGLE_CONTROLS -> {
        if (scaffoldState.bottomSheetState.isVisible) {
          scaffoldState.bottomSheetState.hide()
          onControlsToggled(false)
        } else {
          onControlsToggled(true)
          scaffoldState.bottomSheetState.show()
        }
      }
      Event.SHOW_CALL_INFO -> {
        scaffoldState.bottomSheetState.expand()
      }
      Event.RESTART_HIDE_CONTROLS_TIMER -> {
        restartTimerRequests += 1
      }
    }
  }

  companion object {
    @Composable
    fun rememberCallScreenController(skipHiddenState: Boolean, onControlsToggled: (Boolean) -> Unit): CallScreenController {
      val skip by rememberUpdatedState(skipHiddenState)
      val valueChangeOperation: (SheetValue) -> Boolean = remember {
        {
          !(it == SheetValue.Hidden && skip)
        }
      }

      val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
          confirmValueChange = valueChangeOperation,
          skipHiddenState = skip
        )
      )

      return remember(scaffoldState) {
        CallScreenController(
          scaffoldState = scaffoldState,
          onControlsToggled = onControlsToggled
        )
      }
    }
  }

  enum class Event {
    SWITCH_TO_SPEAKER_VIEW,
    DISMISS_AUDIO_PICKER,
    TOGGLE_CONTROLS,
    SHOW_CALL_INFO,
    RESTART_HIDE_CONTROLS_TIMER
  }
}
