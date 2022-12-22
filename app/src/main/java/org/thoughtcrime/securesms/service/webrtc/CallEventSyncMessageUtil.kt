package org.thoughtcrime.securesms.service.webrtc

import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.ringrtc.RemotePeer
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.CallEvent

/**
 * Helper for creating call event sync messages.
 */
object CallEventSyncMessageUtil {
  @JvmStatic
  fun createAcceptedSyncMessage(remotePeer: RemotePeer, timestamp: Long, isOutgoing: Boolean, isVideoCall: Boolean): CallEvent {
    return CallEvent
      .newBuilder()
      .setPeerUuid(Recipient.resolved(remotePeer.id).requireServiceId().toByteString())
      .setId(remotePeer.callId.longValue())
      .setTimestamp(timestamp)
      .setType(if (isVideoCall) CallEvent.Type.VIDEO_CALL else CallEvent.Type.AUDIO_CALL)
      .setDirection(if (isOutgoing) CallEvent.Direction.OUTGOING else CallEvent.Direction.INCOMING)
      .setEvent(CallEvent.Event.ACCEPTED)
      .build()
  }

  @JvmStatic
  fun createNotAcceptedSyncMessage(remotePeer: RemotePeer, timestamp: Long, isOutgoing: Boolean, isVideoCall: Boolean): CallEvent {
    return CallEvent
      .newBuilder()
      .setPeerUuid(Recipient.resolved(remotePeer.id).requireServiceId().toByteString())
      .setId(remotePeer.callId.longValue())
      .setTimestamp(timestamp)
      .setType(if (isVideoCall) CallEvent.Type.VIDEO_CALL else CallEvent.Type.AUDIO_CALL)
      .setDirection(if (isOutgoing) CallEvent.Direction.OUTGOING else CallEvent.Direction.INCOMING)
      .setEvent(CallEvent.Event.NOT_ACCEPTED)
      .build()
  }
}
