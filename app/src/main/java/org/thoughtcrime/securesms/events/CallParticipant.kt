package org.thoughtcrime.securesms.events

import android.content.Context
import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.ringrtc.CameraState

data class CallParticipant(
  val callParticipantId: CallParticipantId = CallParticipantId(Recipient.UNKNOWN),
  val recipient: Recipient = Recipient.UNKNOWN,
  val identityKey: IdentityKey? = null,
  val videoSink: BroadcastVideoSink = BroadcastVideoSink(),
  val cameraState: CameraState = CameraState.UNKNOWN,
  val isForwardingVideo: Boolean = true,
  val isVideoEnabled: Boolean = false,
  val isMicrophoneEnabled: Boolean = false,
  val handRaisedTimestamp: Long = HAND_LOWERED,
  val lastSpoke: Long = 0,
  val audioLevel: AudioLevel? = null,
  val isMediaKeysReceived: Boolean = true,
  val addedToCallTime: Long = 0,
  val isScreenSharing: Boolean = false,
  private val deviceOrdinal: DeviceOrdinal = DeviceOrdinal.PRIMARY
) {
  val cameraDirection: CameraState.Direction
    get() = if (cameraState.activeDirection == CameraState.Direction.BACK) cameraState.activeDirection else CameraState.Direction.FRONT

  val isMoreThanOneCameraAvailable: Boolean
    get() = cameraState.cameraCount > 1

  val isPrimary: Boolean
    get() = deviceOrdinal == DeviceOrdinal.PRIMARY

  val isSelf: Boolean
    get() = recipient.isSelf

  val isHandRaised: Boolean
    get() = handRaisedTimestamp > 0

  fun getRecipientDisplayName(context: Context): String {
    return if (recipient.isSelf && isPrimary) {
      context.getString(R.string.CallParticipant__you)
    } else if (recipient.isSelf) {
      context.getString(R.string.CallParticipant__you_on_another_device)
    } else if (isPrimary) {
      recipient.getDisplayName(context)
    } else {
      context.getString(R.string.CallParticipant__s_on_another_device, recipient.getDisplayName(context))
    }
  }

  fun getShortRecipientDisplayName(context: Context): String {
    return if (recipient.isSelf && isPrimary) {
      context.getString(R.string.CallParticipant__you)
    } else if (recipient.isSelf) {
      context.getString(R.string.CallParticipant__you_on_another_device)
    } else if (isPrimary) {
      recipient.getShortDisplayName(context)
    } else {
      context.getString(R.string.CallParticipant__s_on_another_device, recipient.getShortDisplayName(context))
    }
  }

  fun withIdentityKey(identityKey: IdentityKey?): CallParticipant {
    return copy(identityKey = identityKey)
  }

  fun withVideoEnabled(videoEnabled: Boolean): CallParticipant {
    return copy(isVideoEnabled = videoEnabled)
  }

  fun withScreenSharingEnabled(enable: Boolean): CallParticipant {
    return copy(isScreenSharing = enable)
  }

  fun withHandRaisedTimestamp(timestamp: Long): CallParticipant {
    return copy(handRaisedTimestamp = timestamp)
  }

  override fun toString(): String {
    return "CallParticipant(callParticipantId=$callParticipantId, isForwardingVideo=$isForwardingVideo, isVideoEnabled=$isVideoEnabled, isMicrophoneEnabled=$isMicrophoneEnabled, handRaisedTimestamp=$handRaisedTimestamp, isMediaKeysReceived=$isMediaKeysReceived, isScreenSharing=$isScreenSharing)"
  }

  enum class DeviceOrdinal {
    PRIMARY,
    SECONDARY
  }

  enum class AudioLevel {
    LOWEST,
    LOW,
    MEDIUM,
    HIGH,
    HIGHEST;

    companion object {

      /**
       * Converts a raw audio level from RingRTC (value in [0, 32767]) to a level suitable for
       * display in the UI.
       */
      @JvmStatic
      fun fromRawAudioLevel(raw: Int): AudioLevel {
        return when {
          raw < 500 -> LOWEST
          raw < 1000 -> LOW
          raw < 5000 -> MEDIUM
          raw < 16000 -> HIGH
          else -> HIGHEST
        }
      }
    }
  }

  companion object {
    const val HAND_LOWERED = -1L

    @JvmField
    val EMPTY: CallParticipant = CallParticipant()

    @JvmStatic
    @JvmOverloads
    fun createLocal(
      cameraState: CameraState,
      renderer: BroadcastVideoSink,
      microphoneEnabled: Boolean,
      handRaisedTimestamp: Long,
      callParticipantId: CallParticipantId = CallParticipantId(Recipient.self())
    ): CallParticipant {
      return CallParticipant(
        callParticipantId = callParticipantId,
        recipient = Recipient.self(),
        videoSink = renderer,
        cameraState = cameraState,
        isVideoEnabled = cameraState.isEnabled && cameraState.cameraCount > 0,
        isMicrophoneEnabled = microphoneEnabled,
        handRaisedTimestamp = handRaisedTimestamp
      )
    }

    @JvmStatic
    fun createRemote(
      callParticipantId: CallParticipantId,
      recipient: Recipient,
      identityKey: IdentityKey?,
      renderer: BroadcastVideoSink,
      isForwardingVideo: Boolean,
      audioEnabled: Boolean,
      videoEnabled: Boolean,
      handRaisedTimestamp: Long,
      lastSpoke: Long,
      mediaKeysReceived: Boolean,
      addedToCallTime: Long,
      isScreenSharing: Boolean,
      deviceOrdinal: DeviceOrdinal
    ): CallParticipant {
      return CallParticipant(
        callParticipantId = callParticipantId,
        recipient = recipient,
        identityKey = identityKey,
        videoSink = renderer,
        isForwardingVideo = isForwardingVideo,
        isVideoEnabled = videoEnabled,
        isMicrophoneEnabled = audioEnabled,
        handRaisedTimestamp = handRaisedTimestamp,
        lastSpoke = lastSpoke,
        isMediaKeysReceived = mediaKeysReceived,
        addedToCallTime = addedToCallTime,
        isScreenSharing = isScreenSharing,
        deviceOrdinal = deviceOrdinal
      )
    }
  }
}
