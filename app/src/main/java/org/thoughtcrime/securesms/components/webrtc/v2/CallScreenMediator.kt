/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.content.Context
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.CallParticipantListUpdate
import org.thoughtcrime.securesms.components.webrtc.WebRtcControls
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.webrtc.CallParticipantsViewState
import org.whispersystems.signalservice.api.messages.calls.HangupMessage

/**
 * Mediates between the activity and the call screen to allow for a consistent API
 * regardless of View or Compose implementation.
 */
interface CallScreenMediator {

  fun setWebRtcCallState(callState: WebRtcViewModel.State)

  fun setControlsAndInfoVisibilityListener(listener: CallControlsVisibilityListener)
  fun onStateRestored()
  fun toggleOverflowPopup()
  fun restartHideControlsTimer()
  fun showCallInfo()
  fun toggleControls()

  fun setControlsListener(controlsListener: CallScreenControlsListener)
  fun setMicEnabled(enabled: Boolean)
  fun setStatus(status: String)
  fun setRecipient(recipient: Recipient)
  fun setWebRtcControls(webRtcControls: WebRtcControls)
  fun updateCallParticipants(callParticipantsViewState: CallParticipantsViewState)
  fun maybeDismissAudioPicker()
  fun setPendingParticipantsViewListener(pendingParticipantsViewListener: PendingParticipantsListener)
  fun updatePendingParticipantsList(pendingParticipantsList: PendingParticipantsState)
  fun setRingGroup(ringGroup: Boolean)
  fun switchToSpeakerView()
  fun enableRingGroup(canRing: Boolean)
  fun showSpeakerViewHint()
  fun hideSpeakerViewHint()
  fun showVideoTooltip(): Dismissible
  fun showCameraTooltip(): Dismissible
  fun onCallStateUpdate(callControlsChange: CallControlsChange)
  fun dismissCallOverflowPopup()
  fun onParticipantListUpdate(callParticipantListUpdate: CallParticipantListUpdate)
  fun enableParticipantUpdatePopup(enabled: Boolean)
  fun enableCallStateUpdatePopup(enabled: Boolean)
  fun showWifiToCellularPopupWindow()

  fun setStatusFromGroupCallState(context: Context, groupCallState: WebRtcViewModel.GroupCallState) {
    when (groupCallState) {
      WebRtcViewModel.GroupCallState.DISCONNECTED -> setStatus(context.getString(R.string.WebRtcCallView__disconnected))
      WebRtcViewModel.GroupCallState.CONNECTING, WebRtcViewModel.GroupCallState.CONNECTED, WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINED -> {
        setStatus("")
      }
      WebRtcViewModel.GroupCallState.RECONNECTING -> setStatus(context.getString(R.string.WebRtcCallView__reconnecting))
      WebRtcViewModel.GroupCallState.CONNECTED_AND_PENDING -> setStatus(context.getString(R.string.WebRtcCallView__waiting_to_be_let_in))
      WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINING -> setStatus(context.getString(R.string.WebRtcCallView__joining))
      else -> Unit
    }
  }

  fun setStatusFromHangupType(context: Context, hangupType: HangupMessage.Type) {
    when (hangupType) {
      HangupMessage.Type.NORMAL, HangupMessage.Type.NEED_PERMISSION -> setStatus(context.getString(R.string.RedPhone_ending_call))
      HangupMessage.Type.ACCEPTED -> setStatus(context.getString(R.string.WebRtcCallActivity__answered_on_a_linked_device))
      HangupMessage.Type.DECLINED -> setStatus(context.getString(R.string.WebRtcCallActivity__declined_on_a_linked_device))
      HangupMessage.Type.BUSY -> setStatus(context.getString(R.string.WebRtcCallActivity__busy_on_a_linked_device))
    }
  }

  companion object {
    fun create(activity: WebRtcCallActivity, viewModel: WebRtcCallViewModel): CallScreenMediator {
      return if (RemoteConfig.newCallUi || (RemoteConfig.internalUser && SignalStore.internal.newCallingUi)) {
        ComposeCallScreenMediator(activity, viewModel)
      } else {
        ViewCallScreenMediator(activity, viewModel)
      }
    }
  }
}

fun interface Dismissible {
  fun dismiss()
}
