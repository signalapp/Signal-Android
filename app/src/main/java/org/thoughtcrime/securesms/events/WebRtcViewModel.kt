package org.thoughtcrime.securesms.events

import com.annimon.stream.OptionalLong
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink
import org.thoughtcrime.securesms.events.CallParticipant.Companion.createLocal
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager
import org.webrtc.PeerConnection

class WebRtcViewModel(state: WebRtcServiceState) {

  enum class State {
    IDLE,

    // Normal states
    CALL_PRE_JOIN,
    CALL_INCOMING,
    CALL_OUTGOING,
    CALL_CONNECTED,
    CALL_RINGING,
    CALL_BUSY,
    CALL_DISCONNECTED,
    CALL_DISCONNECTED_GLARE,
    CALL_NEEDS_PERMISSION,
    CALL_RECONNECTING,

    // Error states
    NETWORK_FAILURE,
    RECIPIENT_UNAVAILABLE,
    NO_SUCH_USER,
    UNTRUSTED_IDENTITY,

    // Multiring Hangup States
    CALL_ACCEPTED_ELSEWHERE,
    CALL_DECLINED_ELSEWHERE,
    CALL_ONGOING_ELSEWHERE;

    val isErrorState: Boolean
      get() = this == NETWORK_FAILURE || this == RECIPIENT_UNAVAILABLE || this == NO_SUCH_USER || this == UNTRUSTED_IDENTITY

    val isPreJoinOrNetworkUnavailable: Boolean
      get() = this == CALL_PRE_JOIN || this == NETWORK_FAILURE

    val isPassedPreJoin: Boolean
      get() = ordinal > ordinal
  }

  enum class GroupCallState {
    IDLE,
    RINGING,
    DISCONNECTED,
    CONNECTING,
    RECONNECTING,
    CONNECTED,
    CONNECTED_AND_JOINING,
    CONNECTED_AND_JOINED;

    val isIdle: Boolean
      get() = this == IDLE

    val isNotIdle: Boolean
      get() = this != IDLE

    val isConnected: Boolean
      get() {
        return when (this) {
          CONNECTED, CONNECTED_AND_JOINING, CONNECTED_AND_JOINED -> true
          else -> false
        }
      }

    val isNotIdleOrConnected: Boolean
      get() {
        return when (this) {
          DISCONNECTED, CONNECTING, RECONNECTING -> true
          else -> false
        }
      }

    val isRinging: Boolean
      get() = this == RINGING
  }

  val state: State = state.callInfoState.callState
  val groupState: GroupCallState = state.callInfoState.groupState
  val recipient: Recipient = state.callInfoState.callRecipient
  val isRemoteVideoOffer: Boolean = state.getCallSetupState(state.callInfoState.activePeer?.callId).isRemoteVideoOffer
  val callConnectedTime: Long = state.callInfoState.callConnectedTime
  val remoteParticipants: List<CallParticipant> = state.callInfoState.remoteCallParticipants
  val identityChangedParticipants: Set<RecipientId> = state.callInfoState.identityChangedRecipients
  val remoteDevicesCount: OptionalLong = state.callInfoState.remoteDevicesCount
  val participantLimit: Long? = state.callInfoState.participantLimit

  @get:JvmName("shouldRingGroup")
  val ringGroup: Boolean = state.getCallSetupState(state.callInfoState.activePeer?.callId).ringGroup
  val ringerRecipient: Recipient = state.getCallSetupState(state.callInfoState.activePeer?.callId).ringerRecipient
  val activeDevice: SignalAudioManager.AudioDevice = state.localDeviceState.activeDevice
  val availableDevices: Set<SignalAudioManager.AudioDevice> = state.localDeviceState.availableDevices
  val bluetoothPermissionDenied: Boolean = state.localDeviceState.bluetoothPermissionDenied

  val localParticipant: CallParticipant = createLocal(
    state.localDeviceState.cameraState,
    (if (state.videoState.localSink != null) state.videoState.localSink else BroadcastVideoSink())!!,
    state.localDeviceState.isMicrophoneEnabled
  )

  val isCellularConnection: Boolean = when (state.localDeviceState.networkConnectionType) {
    PeerConnection.AdapterType.UNKNOWN,
    PeerConnection.AdapterType.ETHERNET,
    PeerConnection.AdapterType.WIFI,
    PeerConnection.AdapterType.VPN,
    PeerConnection.AdapterType.LOOPBACK,
    PeerConnection.AdapterType.ADAPTER_TYPE_ANY -> false
    PeerConnection.AdapterType.CELLULAR,
    PeerConnection.AdapterType.CELLULAR_2G,
    PeerConnection.AdapterType.CELLULAR_3G,
    PeerConnection.AdapterType.CELLULAR_4G,
    PeerConnection.AdapterType.CELLULAR_5G -> true
  }

  val isRemoteVideoEnabled: Boolean
    get() = remoteParticipants.any(CallParticipant::isVideoEnabled) || groupState.isNotIdle && remoteParticipants.size > 1

  fun areRemoteDevicesInCall(): Boolean {
    return remoteDevicesCount.isPresent && remoteDevicesCount.asLong > 0
  }

  override fun toString(): String {
    return """
      WebRtcViewModel {
       state=$state,
       recipient=${recipient.id},
       isRemoteVideoOffer=$isRemoteVideoOffer,
       callConnectedTime=$callConnectedTime,
       localParticipant=$localParticipant,
       remoteParticipants=$remoteParticipants,
       identityChangedRecipients=$identityChangedParticipants,
       remoteDevicesCount=$remoteDevicesCount,
       participantLimit=$participantLimit,
       activeDevice=$activeDevice,
       availableDevices=$availableDevices,
       bluetoothPermissionDenied=$bluetoothPermissionDenied,
       ringGroup=$ringGroup
      }
    """.trimIndent()
  }
}
