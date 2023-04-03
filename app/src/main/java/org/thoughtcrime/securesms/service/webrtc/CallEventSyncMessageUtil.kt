package org.thoughtcrime.securesms.service.webrtc

import com.google.protobuf.ByteString
import org.thoughtcrime.securesms.database.model.toProtoByteString
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.ringrtc.RemotePeer
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.CallEvent

/**
 * Helper for creating call event sync messages.
 */
object CallEventSyncMessageUtil {
  @JvmStatic
  fun createAcceptedSyncMessage(remotePeer: RemotePeer, timestamp: Long, isOutgoing: Boolean, isVideoCall: Boolean): CallEvent {
    return createCallEvent(
      remotePeer.id,
      remotePeer.callId.longValue(),
      timestamp,
      isOutgoing,
      isVideoCall,
      CallEvent.Event.ACCEPTED
    )
  }

  @JvmStatic
  fun createNotAcceptedSyncMessage(remotePeer: RemotePeer, timestamp: Long, isOutgoing: Boolean, isVideoCall: Boolean): CallEvent {
    return createCallEvent(
      remotePeer.id,
      remotePeer.callId.longValue(),
      timestamp,
      isOutgoing,
      isVideoCall,
      CallEvent.Event.NOT_ACCEPTED
    )
  }

  @JvmStatic
  fun createDeleteCallEvent(remotePeer: RemotePeer, timestamp: Long, isOutgoing: Boolean, isVideoCall: Boolean): CallEvent {
    return createCallEvent(
      remotePeer.id,
      remotePeer.callId.longValue(),
      timestamp,
      isOutgoing,
      isVideoCall,
      CallEvent.Event.DELETE
    )
  }

  private fun createCallEvent(
    recipientId: RecipientId,
    callId: Long,
    timestamp: Long,
    isOutgoing: Boolean,
    isVideoCall: Boolean,
    event: CallEvent.Event
  ): CallEvent {
    val recipient = Recipient.resolved(recipientId)
    val isGroupCall = recipient.isGroup
    val conversationId: ByteString = if (isGroupCall) {
      recipient.requireGroupId().decodedId.toProtoByteString()
    } else {
      recipient.requireServiceId().toByteString()
    }

    return CallEvent
      .newBuilder()
      .setConversationId(conversationId)
      .setId(callId)
      .setTimestamp(timestamp)
      .setType(
        when {
          isGroupCall -> CallEvent.Type.GROUP_CALL
          isVideoCall -> CallEvent.Type.VIDEO_CALL
          else -> CallEvent.Type.AUDIO_CALL
        }
      )
      .setDirection(if (isOutgoing) CallEvent.Direction.OUTGOING else CallEvent.Direction.INCOMING)
      .setEvent(event)
      .build()
  }
}
