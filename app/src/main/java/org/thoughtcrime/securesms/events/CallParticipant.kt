package org.thoughtcrime.securesms.events

import android.content.Context
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.ringrtc.CameraState
import org.whispersystems.libsignal.IdentityKey

data class CallParticipant constructor(
  val callParticipantId: CallParticipantId = CallParticipantId(Recipient.UNKNOWN),
  val recipient: Recipient = Recipient.UNKNOWN,
  val identityKey: IdentityKey? = null,
  val videoSink: BroadcastVideoSink = BroadcastVideoSink(),
  val cameraState: CameraState = CameraState.UNKNOWN,
  val isVideoEnabled: Boolean = false,
  val isMicrophoneEnabled: Boolean = false,
  val lastSpoke: Long = 0,
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

  enum class DeviceOrdinal {
    PRIMARY, SECONDARY
  }

  companion object {
    @JvmField
    val EMPTY: CallParticipant = CallParticipant()

    @JvmStatic
    fun createLocal(
      cameraState: CameraState,
      renderer: BroadcastVideoSink,
      microphoneEnabled: Boolean
    ): CallParticipant {
      return CallParticipant(
        callParticipantId = CallParticipantId(Recipient.self()),
        recipient = Recipient.self(),
        videoSink = renderer,
        cameraState = cameraState,
        isVideoEnabled = cameraState.isEnabled && cameraState.cameraCount > 0,
        isMicrophoneEnabled = microphoneEnabled
      )
    }

    @JvmStatic
    fun createRemote(
      callParticipantId: CallParticipantId,
      recipient: Recipient,
      identityKey: IdentityKey?,
      renderer: BroadcastVideoSink,
      audioEnabled: Boolean,
      videoEnabled: Boolean,
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
        isVideoEnabled = videoEnabled,
        isMicrophoneEnabled = audioEnabled,
        lastSpoke = lastSpoke,
        isMediaKeysReceived = mediaKeysReceived,
        addedToCallTime = addedToCallTime,
        isScreenSharing = isScreenSharing,
        deviceOrdinal = deviceOrdinal
      )
    }
  }
}
