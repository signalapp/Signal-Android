/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.os.Build
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.components.webrtc.ToggleButtonOutputState
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioDevice

/**
 * Collects and manages state objects for manipulating the call screen UI programatically.
 */
@Stable
@OptIn(ExperimentalMaterial3Api::class)
class CallScreenController private constructor(
  val scaffoldState: BottomSheetScaffoldState,
  val audioOutputPickerController: AudioOutputPickerController,
  val callParticipantsVerticalPagerState: PagerState,
  val onControlsToggled: (Boolean) -> Unit
) {

  var restartTimerRequests by mutableLongStateOf(0L)

  suspend fun handleEvent(event: Event) {
    when (event) {
      Event.SWITCH_TO_SPEAKER_VIEW -> {
        callParticipantsVerticalPagerState.animateScrollToPage(1)
      }

      Event.DISMISS_AUDIO_PICKER -> {
        audioOutputPickerController.hide()
      }

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
    fun rememberCallScreenController(
      skipHiddenState: Boolean,
      onControlsToggled: (Boolean) -> Unit,
      callControlsState: CallControlsState,
      callControlsListener: CallScreenControlsListener
    ): CallScreenController {
      val skip by rememberUpdatedState(skipHiddenState)
      val valueChangeOperation: (SheetValue) -> Boolean = remember {
        {
          !(it == SheetValue.Hidden && skip)
        }
      }

      val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberCallScreenSheetState(
          confirmValueChange = valueChangeOperation,
          skipHiddenState = skip
        )
      )

      val onSelectedAudioDeviceChanged: (WebRtcAudioDevice) -> Unit = remember {
        {
          if (Build.VERSION.SDK_INT >= 31) {
            callControlsListener.onAudioOutputChanged31(it)
          } else {
            callControlsListener.onAudioOutputChanged(it.webRtcAudioOutput)
          }
        }
      }

      val audioOutputPickerOutputState = remember {
        ToggleButtonOutputState().apply {
          isEarpieceAvailable = callControlsState.isEarpieceAvailable
          isWiredHeadsetAvailable = callControlsState.isWiredHeadsetAvailable
          isBluetoothHeadsetAvailable = callControlsState.isBluetoothHeadsetAvailable
        }
      }

      LaunchedEffect(callControlsState.isEarpieceAvailable, callControlsState.isWiredHeadsetAvailable, callControlsState.isBluetoothHeadsetAvailable) {
        audioOutputPickerOutputState.apply {
          isEarpieceAvailable = callControlsState.isEarpieceAvailable
          isWiredHeadsetAvailable = callControlsState.isWiredHeadsetAvailable
          isBluetoothHeadsetAvailable = callControlsState.isBluetoothHeadsetAvailable
        }
      }

      val audioOutputPickerController = rememberAudioOutputPickerController(
        onSelectedDeviceChanged = onSelectedAudioDeviceChanged,
        outputState = audioOutputPickerOutputState
      )

      val callParticipantsVerticalPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 2 }
      )

      return remember(scaffoldState, callParticipantsVerticalPagerState, audioOutputPickerController) {
        CallScreenController(
          scaffoldState = scaffoldState,
          onControlsToggled = onControlsToggled,
          callParticipantsVerticalPagerState = callParticipantsVerticalPagerState,
          audioOutputPickerController = audioOutputPickerController
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

/**
 * Replaces `rememberStandardBottomSheetState` as it appeared to have a bug when the skipHiddenState value would
 * change before restore.
 */
@Composable
@ExperimentalMaterial3Api
private fun rememberCallScreenSheetState(
  confirmValueChange: (SheetValue) -> Boolean = { true },
  initialValue: SheetValue = SheetValue.PartiallyExpanded,
  skipHiddenState: Boolean = false
): SheetState {
  val density = LocalDensity.current
  return rememberSaveable(
    confirmValueChange,
    skipHiddenState,
    saver = saveSheetState(
      confirmValueChange = confirmValueChange,
      density = density,
      skipHiddenState = skipHiddenState
    )
  ) {
    SheetState(
      skipPartiallyExpanded = false,
      initialValue = initialValue,
      confirmValueChange = confirmValueChange,
      positionalThreshold = { with(density) { 56.dp.toPx() } },
      velocityThreshold = { with(density) { 125.dp.toPx() } },
      skipHiddenState = skipHiddenState
    )
  }
}

/**
 * Because we have a dynamic value for `skipHiddenState` we want to make sure we appropriately
 * set the SheetValue on restore to avoid a crash.
 */
@ExperimentalMaterial3Api
private fun saveSheetState(
  confirmValueChange: (SheetValue) -> Boolean,
  density: Density,
  skipHiddenState: Boolean
): Saver<SheetState, SheetValue> {
  return Saver(
    save = { it.currentValue },
    restore = { savedValue ->
      val value = if (savedValue == SheetValue.Hidden && skipHiddenState) {
        SheetValue.PartiallyExpanded
      } else {
        savedValue
      }

      SheetState(
        skipPartiallyExpanded = false,
        positionalThreshold = { with(density) { 56.dp.toPx() } },
        velocityThreshold = { with(density) { 125.dp.toPx() } },
        initialValue = value,
        confirmValueChange = confirmValueChange,
        skipHiddenState = skipHiddenState
      )
    }
  )
}
