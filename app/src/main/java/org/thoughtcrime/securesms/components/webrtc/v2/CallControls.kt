/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.signal.core.ui.DarkPreview
import org.signal.core.ui.Previews
import org.signal.core.ui.TriggerAlignedPopupState.Companion.popupTrigger
import org.signal.core.ui.TriggerAlignedPopupState.Companion.rememberTriggerAlignedPopupState
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.CallParticipantsState
import org.thoughtcrime.securesms.components.webrtc.ToggleButtonOutputState
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioDevice
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioOutput
import org.thoughtcrime.securesms.components.webrtc.WebRtcControls
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * Renders the button strip / start call button in the call screen
 * bottom sheet.
 */
@Composable
fun CallControls(
  displayVideoTooltip: Boolean,
  callControlsState: CallControlsState,
  callScreenControlsListener: CallScreenControlsListener,
  callScreenSheetDisplayListener: CallScreenSheetDisplayListener,
  additionalActionsState: AdditionalActionsState,
  modifier: Modifier = Modifier
) {
  val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

  val density = LocalDensity.current
  val padBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
  var bottom by remember {
    mutableStateOf(padBottom)
  }

  if (padBottom != 0.dp) {
    bottom = padBottom
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = spacedBy(30.dp),
    modifier = modifier.padding(bottom = bottom)
  ) {
    Row(
      horizontalArrangement = spacedBy(20.dp)
    ) {
      if (callControlsState.displayAudioOutputToggle) {
        val outputState = remember {
          ToggleButtonOutputState().apply {
            isEarpieceAvailable = callControlsState.isEarpieceAvailable
            isWiredHeadsetAvailable = callControlsState.isWiredHeadsetAvailable
            isBluetoothHeadsetAvailable = callControlsState.isBluetoothHeadsetAvailable
          }
        }

        LaunchedEffect(callControlsState.isEarpieceAvailable, callControlsState.isWiredHeadsetAvailable, callControlsState.isBluetoothHeadsetAvailable) {
          outputState.apply {
            isEarpieceAvailable = callControlsState.isEarpieceAvailable
            isWiredHeadsetAvailable = callControlsState.isWiredHeadsetAvailable
            isBluetoothHeadsetAvailable = callControlsState.isBluetoothHeadsetAvailable
          }
        }

        val onSelectedAudioDeviceChanged: (WebRtcAudioDevice) -> Unit = remember {
          {
            if (Build.VERSION.SDK_INT >= 31) {
              callScreenControlsListener.onAudioOutputChanged31(it)
            } else {
              callScreenControlsListener.onAudioOutputChanged(it.webRtcAudioOutput)
            }
          }
        }

        CallAudioToggleButton(
          outputState = outputState,
          contentDescription = stringResource(id = R.string.WebRtcAudioOutputToggle__audio_output),
          onSelectedDeviceChanged = onSelectedAudioDeviceChanged,
          onSheetDisplayChanged = callScreenSheetDisplayListener::onAudioDeviceSheetDisplayChanged
        )
      }

      val hasCameraPermission = ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
      if (callControlsState.displayVideoToggle) {
        CallScreenTooltipBox(
          text = stringResource(R.string.WebRtcCallActivity__tap_here_to_turn_on_your_video),
          displayTooltip = displayVideoTooltip,
          onTooltipDismissed = callScreenSheetDisplayListener::onVideoTooltipDismissed
        ) {
          ToggleVideoButton(
            isVideoEnabled = callControlsState.isVideoEnabled && hasCameraPermission,
            onChange = callScreenControlsListener::onVideoChanged
          )
        }
      }

      val hasRecordAudioPermission = ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
      if (callControlsState.displayMicToggle) {
        ToggleMicButton(
          isMicEnabled = callControlsState.isMicEnabled && hasRecordAudioPermission,
          onChange = callScreenControlsListener::onMicChanged
        )
      }

      if (callControlsState.displayGroupRingingToggle) {
        ToggleRingButton(
          isRingEnabled = callControlsState.isGroupRingingEnabled,
          isRingAllowed = callControlsState.isGroupRingingAllowed,
          onChange = callScreenControlsListener::onRingGroupChanged
        )
      }

      if (callControlsState.displayAdditionalActions) {
        AdditionalActionsButton(
          onClick = callScreenControlsListener::onOverflowClicked,
          modifier = Modifier.popupTrigger(additionalActionsState.triggerAlignedPopupState)
        )
      }

      if (callControlsState.displayEndCallButton) {
        HangupButton(onClick = callScreenControlsListener::onEndCallPressed)
      }

      if (callControlsState.displayStartCallButton && !isPortrait) {
        StartCallButton(
          text = stringResource(callControlsState.startCallButtonText),
          onClick = {
            callScreenControlsListener.onStartCall(callControlsState.isVideoEnabled)
          }
        )
      }
    }

    if (callControlsState.displayStartCallButton && isPortrait) {
      StartCallButton(
        text = stringResource(callControlsState.startCallButtonText),
        onClick = {
          callScreenControlsListener.onStartCall(callControlsState.isVideoEnabled)
        }
      )
    }
  }
}

@DarkPreview
@Composable
fun CallControlsPreview() {
  Previews.Preview {
    CallControls(
      callControlsState = CallControlsState(
        displayAudioOutputToggle = true,
        audioOutput = WebRtcAudioOutput.WIRED_HEADSET,
        displayMicToggle = true,
        isMicEnabled = true,
        displayVideoToggle = true,
        isVideoEnabled = true,
        displayGroupRingingToggle = true,
        isGroupRingingEnabled = true,
        displayAdditionalActions = true,
        displayStartCallButton = true,
        startCallButtonText = R.string.WebRtcCallView__start_call,
        displayEndCallButton = true
      ),
      displayVideoTooltip = false,
      callScreenControlsListener = CallScreenControlsListener.Empty,
      callScreenSheetDisplayListener = CallScreenSheetDisplayListener.Empty,
      additionalActionsState = AdditionalActionsState(
        triggerAlignedPopupState = rememberTriggerAlignedPopupState()
      )
    )
  }
}

/**
 * Callbacks for call controls actions.
 */
interface CallScreenSheetDisplayListener {
  fun onAudioDeviceSheetDisplayChanged(displayed: Boolean)
  fun onOverflowDisplayChanged(displayed: Boolean)
  fun onVideoTooltipDismissed()

  object Empty : CallScreenSheetDisplayListener {
    override fun onAudioDeviceSheetDisplayChanged(displayed: Boolean) = Unit
    override fun onOverflowDisplayChanged(displayed: Boolean) = Unit
    override fun onVideoTooltipDismissed() = Unit
  }
}

/**
 * State object representing how the controls should appear. Since these values are
 * gleaned from multiple data sources, this object represents the amalgamation of those
 * sources so we don't need to listen to multiple here.
 */
data class CallControlsState(
  val isEarpieceAvailable: Boolean = false,
  val isBluetoothHeadsetAvailable: Boolean = false,
  val isWiredHeadsetAvailable: Boolean = false,
  val skipHiddenState: Boolean = true,
  val displayAudioOutputToggle: Boolean = false,
  val audioOutput: WebRtcAudioOutput = WebRtcAudioOutput.HANDSET,
  val displayVideoToggle: Boolean = false,
  val isVideoEnabled: Boolean = false,
  val displayMicToggle: Boolean = false,
  val isMicEnabled: Boolean = false,
  val displayGroupRingingToggle: Boolean = false,
  val isGroupRingingEnabled: Boolean = false,
  val isGroupRingingAllowed: Boolean = false,
  val displayAdditionalActions: Boolean = false,
  val displayStartCallButton: Boolean = false,
  val startCallButtonText: Int = R.string.WebRtcCallView__start_call,
  val displayEndCallButton: Boolean = false
) {
  companion object {
    /**
     * Presentation-level method to build out the controls state from legacy objects.
     */
    @JvmStatic
    fun fromViewModelData(
      callParticipantsState: CallParticipantsState,
      webRtcControls: WebRtcControls,
      groupMemberCount: Int
    ): CallControlsState {
      val isGroupRingingEnabled = if (callParticipantsState.callState == WebRtcViewModel.State.CALL_PRE_JOIN) {
        callParticipantsState.groupCallState.isNotIdle
      } else {
        callParticipantsState.ringGroup
      }

      return CallControlsState(
        isEarpieceAvailable = webRtcControls.isEarpieceAvailableForAudioToggle,
        isBluetoothHeadsetAvailable = webRtcControls.isBluetoothHeadsetAvailableForAudioToggle,
        isWiredHeadsetAvailable = webRtcControls.isWiredHeadsetAvailableForAudioToggle,
        skipHiddenState = !(webRtcControls.isFadeOutEnabled || webRtcControls == WebRtcControls.PIP || webRtcControls.displayErrorControls()),
        displayAudioOutputToggle = webRtcControls.displayAudioToggle(),
        audioOutput = webRtcControls.audioOutput,
        displayVideoToggle = webRtcControls.displayVideoToggle(),
        isVideoEnabled = callParticipantsState.localParticipant.isVideoEnabled,
        displayMicToggle = webRtcControls.displayMuteAudio(),
        isMicEnabled = callParticipantsState.localParticipant.isMicrophoneEnabled,
        displayGroupRingingToggle = webRtcControls.displayRingToggle(),
        isGroupRingingEnabled = isGroupRingingEnabled,
        isGroupRingingAllowed = groupMemberCount <= RemoteConfig.maxGroupCallRingSize,
        displayAdditionalActions = webRtcControls.displayOverflow(),
        displayStartCallButton = webRtcControls.displayStartCallControls(),
        startCallButtonText = webRtcControls.startCallButtonText,
        displayEndCallButton = webRtcControls.displayEndCall()
      )
    }
  }
}
