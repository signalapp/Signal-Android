/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.os.Build
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioDevice
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioOutput
import org.thoughtcrime.securesms.components.webrtc.WebRtcCallViewModel
import org.thoughtcrime.securesms.components.webrtc.controls.ControlsAndInfoViewModel
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.SignalCallManager
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager
import org.whispersystems.signalservice.api.messages.calls.HangupMessage
import kotlin.time.Duration.Companion.milliseconds

/**
 * Presentation logic and state holder for information that was generally done
 * in-activity for the V1 call screen.
 */
class CallViewModel(
  private val webRtcCallViewModel: WebRtcCallViewModel,
  private val controlsAndInfoViewModel: ControlsAndInfoViewModel
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(CallViewModel::class)
  }

  private var previousEvent: WebRtcViewModel? = null
  private var enableVideoIfAvailable = false

  private val internalCallScreenState = MutableStateFlow(CallScreenState())
  val callScreenState: StateFlow<CallScreenState> = internalCallScreenState

  fun unregisterEventBus() {
    EventBus.getDefault().unregister(this)
  }

  fun onMicToggledChanged(enabled: Boolean) {
    AppDependencies.signalCallManager.setMuteAudio(!enabled)

    val update = if (enabled) CallControlsChange.MIC_ON else CallControlsChange.MIC_OFF
    performCallStateUpdateChange(update)
  }

  fun onVideoToggleChanged(enabled: Boolean) {
    AppDependencies.signalCallManager.setEnableVideo(enabled)
  }

  fun onGroupRingAllowedChanged(allowed: Boolean) {
    AppDependencies.signalCallManager.setRingGroup(allowed)
  }

  fun onAdditionalActionsClick() {
    // TODO Toggle overflow popup
  }

  /**
   * Denies the call. If successful, returns true.
   */
  fun deny() {
    val recipient = webRtcCallViewModel.recipient.get()
    if (recipient != Recipient.UNKNOWN) {
      AppDependencies.signalCallManager.denyCall()

      internalCallScreenState.update {
        it.copy(
          callStatus = CallString.ResourceString(R.string.RedPhone_ending_call),
          hangup = CallScreenState.Hangup(
            hangupMessageType = HangupMessage.Type.NORMAL
          )
        )
      }
    }
  }

  fun hangup() {
    Log.i(TAG, "Hangup pressed, handling termination now...")
    AppDependencies.signalCallManager.localHangup()

    internalCallScreenState.update {
      it.copy(
        hangup = CallScreenState.Hangup(
          hangupMessageType = HangupMessage.Type.NORMAL
        )
      )
    }
  }

  fun onGroupRingToggleChanged(enabled: Boolean, allowed: Boolean) {
    if (allowed) {
      AppDependencies.signalCallManager.setRingGroup(enabled)
      val update = if (enabled) CallControlsChange.RINGING_ON else CallControlsChange.RINGING_OFF
      performCallStateUpdateChange(update)
    } else {
      AppDependencies.signalCallManager.setRingGroup(false)
      performCallStateUpdateChange(CallControlsChange.RINGING_DISABLED)
    }
  }

  fun onCallEvent(event: CallEvent) {
    when (event) {
      CallEvent.DismissSwitchCameraTooltip -> Unit // TODO
      CallEvent.DismissVideoTooltip -> Unit // TODO
      is CallEvent.ShowGroupCallSafetyNumberChange -> Unit // TODO
      CallEvent.ShowSwipeToSpeakerHint -> Unit // TODO
      CallEvent.ShowSwitchCameraTooltip -> Unit // TODO
      CallEvent.ShowVideoTooltip -> Unit // TODO
      CallEvent.ShowWifiToCellularPopup -> Unit // TODO
      is CallEvent.StartCall -> startCall(event.isVideoCall)
      CallEvent.SwitchToSpeaker -> Unit // TODO
    }
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onWebRtcEvent(event: WebRtcViewModel) {
    Log.i(TAG, "Got message from service: " + event.describeDifference(previousEvent))
    previousEvent = event

    webRtcCallViewModel.setRecipient(event.recipient)
    internalCallScreenState.update {
      it.copy(
        callRecipientId = event.recipient.id
      )
    }
    controlsAndInfoViewModel.setRecipient(event.recipient)

    when (event.state) {
      WebRtcViewModel.State.IDLE -> Unit
      WebRtcViewModel.State.CALL_PRE_JOIN -> handlePreJoin(event)
      WebRtcViewModel.State.CALL_INCOMING -> Unit
      WebRtcViewModel.State.CALL_OUTGOING -> handleOutgoing(event)
      WebRtcViewModel.State.CALL_CONNECTED -> handleConnected(event)
      WebRtcViewModel.State.CALL_RINGING -> handleRinging()
      WebRtcViewModel.State.CALL_BUSY -> handleBusy()
      WebRtcViewModel.State.CALL_DISCONNECTED -> handleCallTerminated(HangupMessage.Type.NORMAL)
      WebRtcViewModel.State.CALL_DISCONNECTED_GLARE -> handleGlare(event.recipient.id)
      WebRtcViewModel.State.CALL_NEEDS_PERMISSION -> handleCallTerminated(HangupMessage.Type.NEED_PERMISSION)
      WebRtcViewModel.State.CALL_RECONNECTING -> handleReconnecting()
      WebRtcViewModel.State.NETWORK_FAILURE -> handleNetworkFailure()
      WebRtcViewModel.State.RECIPIENT_UNAVAILABLE -> handleRecipientUnavailable()
      WebRtcViewModel.State.NO_SUCH_USER -> Unit // TODO
      WebRtcViewModel.State.UNTRUSTED_IDENTITY -> Unit // TODO
      WebRtcViewModel.State.CALL_ACCEPTED_ELSEWHERE -> handleCallTerminated(HangupMessage.Type.ACCEPTED)
      WebRtcViewModel.State.CALL_DECLINED_ELSEWHERE -> handleCallTerminated(HangupMessage.Type.DECLINED)
      WebRtcViewModel.State.CALL_ONGOING_ELSEWHERE -> handleCallTerminated(HangupMessage.Type.BUSY)
    }

    // TODO [alex] -- Call link handling block

    val enableVideo = event.localParticipant.cameraState.cameraCount > 0 && enableVideoIfAvailable
    webRtcCallViewModel.updateFromWebRtcViewModel(event, enableVideo)

    // TODO [alex] -- handle enable video

    // TODO [alex] -- handle denied bluetooth permission
  }

  private fun startCall(isVideoCall: Boolean) {
    enableVideoIfAvailable = isVideoCall

    if (isVideoCall) {
      AppDependencies.signalCallManager.startOutgoingVideoCall(webRtcCallViewModel.recipient.get())
    } else {
      AppDependencies.signalCallManager.startOutgoingAudioCall(webRtcCallViewModel.recipient.get())
    }

    MessageSender.onMessageSent()
  }

  private fun performCallStateUpdateChange(update: CallControlsChange) {
    viewModelScope.launch {
      internalCallScreenState.update {
        it.copy(callControlsChange = update)
      }

      delay(1000)

      internalCallScreenState.update {
        if (it.callControlsChange == update) {
          it.copy(callControlsChange = null)
        } else {
          it
        }
      }
    }
  }

  private fun handlePreJoin(event: WebRtcViewModel) {
    if (event.groupState.isNotIdle && event.ringGroup && event.areRemoteDevicesInCall()) {
      AppDependencies.signalCallManager.setRingGroup(false)
    }
  }

  private fun handleOutgoing(event: WebRtcViewModel) {
    val status = if (event.groupState.isNotIdle) {
      getStatusFromGroupState(event.groupState)
    } else {
      CallString.ResourceString(R.string.WebRtcCallActivity__calling)
    }

    internalCallScreenState.update {
      it.copy(callStatus = status)
    }
  }

  private fun handleConnected(event: WebRtcViewModel) {
    if (event.groupState.isNotIdleOrConnected) {
      val status = getStatusFromGroupState(event.groupState)

      internalCallScreenState.update {
        it.copy(callStatus = status)
      }
    }
  }

  private fun handleRinging() {
    internalCallScreenState.update {
      it.copy(callStatus = CallString.ResourceString(R.string.RedPhone_ringing))
    }
  }

  private fun handleBusy() {
    EventBus.getDefault().removeStickyEvent(WebRtcViewModel::class.java)
    internalCallScreenState.update {
      it.copy(callStatus = CallString.ResourceString(R.string.RedPhone_busy))
    }

    internalCallScreenState.update {
      it.copy(
        hangup = CallScreenState.Hangup(
          hangupMessageType = HangupMessage.Type.BUSY,
          delay = SignalCallManager.BUSY_TONE_LENGTH.milliseconds
        )
      )
    }
  }

  private fun handleGlare(recipientId: RecipientId) {
    Log.i(TAG, "handleGlare: $recipientId")

    internalCallScreenState.update {
      it.copy(callStatus = null)
    }
  }

  private fun handleReconnecting() {
    internalCallScreenState.update {
      it.copy(callStatus = CallString.ResourceString(R.string.WebRtcCallView__reconnecting))
    }
  }

  private fun handleNetworkFailure() {
    EventBus.getDefault().removeStickyEvent(WebRtcViewModel::class.java)

    internalCallScreenState.update {
      it.copy(callStatus = CallString.ResourceString(R.string.RedPhone_network_failed))
    }
  }

  private fun handleRecipientUnavailable() {
  }

  private fun handleCallTerminated(hangupType: HangupMessage.Type) {
    Log.i(TAG, "handleTerminate called: " + hangupType.name)

    EventBus.getDefault().removeStickyEvent(WebRtcViewModel::class.java)

    internalCallScreenState.update {
      it.copy(
        callStatus = CallString.ResourceString(getStatusFromHangupType(hangupType)),
        hangup = CallScreenState.Hangup(
          hangupMessageType = hangupType
        )
      )
    }
  }

  @StringRes
  private fun getStatusFromHangupType(hangupType: HangupMessage.Type): Int {
    return when (hangupType) {
      HangupMessage.Type.NORMAL, HangupMessage.Type.NEED_PERMISSION -> R.string.RedPhone_ending_call
      HangupMessage.Type.ACCEPTED -> R.string.WebRtcCallActivity__answered_on_a_linked_device
      HangupMessage.Type.DECLINED -> R.string.WebRtcCallActivity__declined_on_a_linked_device
      HangupMessage.Type.BUSY -> R.string.WebRtcCallActivity__busy_on_a_linked_device
    }
  }

  private fun getStatusFromGroupState(groupState: WebRtcViewModel.GroupCallState): CallString? {
    return when (groupState) {
      WebRtcViewModel.GroupCallState.DISCONNECTED -> CallString.ResourceString(R.string.WebRtcCallView__disconnected)
      WebRtcViewModel.GroupCallState.RECONNECTING -> CallString.ResourceString(R.string.WebRtcCallView__reconnecting)
      WebRtcViewModel.GroupCallState.CONNECTED_AND_PENDING -> CallString.ResourceString(R.string.WebRtcCallView__joining)
      WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINING -> CallString.ResourceString(R.string.WebRtcCallView__waiting_to_be_let_in)
      else -> null
    }
  }

  fun onAudioDeviceSheetDisplayChanged(displayed: Boolean) {
    internalCallScreenState.update {
      it.copy(isDisplayingAudioToggleSheet = displayed)
    }
  }

  fun onSelectedAudioDeviceChanged(audioDevice: WebRtcAudioDevice) {
    // TODO [alex]     maybeDisplaySpeakerphonePopup(audioOutput);
    if (Build.VERSION.SDK_INT >= 31) {
      AppDependencies.signalCallManager.selectAudioDevice(SignalAudioManager.ChosenAudioDeviceIdentifier(audioDevice.deviceId!!))
    } else {
      val managerDevice = when (audioDevice.webRtcAudioOutput) {
        WebRtcAudioOutput.HANDSET -> SignalAudioManager.AudioDevice.EARPIECE
        WebRtcAudioOutput.SPEAKER -> SignalAudioManager.AudioDevice.SPEAKER_PHONE
        WebRtcAudioOutput.BLUETOOTH_HEADSET -> SignalAudioManager.AudioDevice.BLUETOOTH
        WebRtcAudioOutput.WIRED_HEADSET -> SignalAudioManager.AudioDevice.WIRED_HEADSET
      }

      AppDependencies.signalCallManager.selectAudioDevice(SignalAudioManager.ChosenAudioDeviceIdentifier(managerDevice))
    }
  }
}
