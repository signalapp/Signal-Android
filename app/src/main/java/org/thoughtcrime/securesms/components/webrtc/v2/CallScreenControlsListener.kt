/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.annotation.RequiresApi
import org.thoughtcrime.securesms.components.webrtc.CallParticipantsState
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioDevice
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioOutput

/**
 * Mediator callbacks for call screen signals.
 */
interface CallScreenControlsListener {
  fun onStartCall(isVideoCall: Boolean)
  fun onCancelStartCall()
  fun onAudioOutputChanged(audioOutput: WebRtcAudioOutput)

  @RequiresApi(31)
  fun onAudioOutputChanged31(audioOutput: WebRtcAudioDevice)
  fun onVideoChanged(isVideoEnabled: Boolean)
  fun onMicChanged(isMicEnabled: Boolean)
  fun onOverflowClicked()
  fun onCameraDirectionChanged()
  fun onEndCallPressed()
  fun onDenyCallPressed()
  fun onAcceptCallWithVoiceOnlyPressed()
  fun onAcceptCallPressed()
  fun onPageChanged(page: CallParticipantsState.SelectedPage)
  fun onLocalPictureInPictureClicked()
  fun onRingGroupChanged(ringGroup: Boolean, ringingAllowed: Boolean)
  fun onCallInfoClicked()
  fun onNavigateUpClicked()
  fun toggleControls()
  fun onAudioPermissionsRequested(onGranted: Runnable?)

  object Empty : CallScreenControlsListener {
    override fun onStartCall(isVideoCall: Boolean) = Unit
    override fun onCancelStartCall() = Unit
    override fun onAudioOutputChanged(audioOutput: WebRtcAudioOutput) = Unit
    override fun onAudioOutputChanged31(audioOutput: WebRtcAudioDevice) = Unit
    override fun onVideoChanged(isVideoEnabled: Boolean) = Unit
    override fun onMicChanged(isMicEnabled: Boolean) = Unit
    override fun onOverflowClicked() = Unit
    override fun onCameraDirectionChanged() = Unit
    override fun onEndCallPressed() = Unit
    override fun onDenyCallPressed() = Unit
    override fun onAcceptCallWithVoiceOnlyPressed() = Unit
    override fun onAcceptCallPressed() = Unit
    override fun onPageChanged(page: CallParticipantsState.SelectedPage) = Unit
    override fun onLocalPictureInPictureClicked() = Unit
    override fun onRingGroupChanged(ringGroup: Boolean, ringingAllowed: Boolean) = Unit
    override fun onCallInfoClicked() = Unit
    override fun onNavigateUpClicked() = Unit
    override fun toggleControls() = Unit
    override fun onAudioPermissionsRequested(onGranted: Runnable?) = Unit
  }
}
