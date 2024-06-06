package org.thoughtcrime.securesms.service.webrtc

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.ringrtc.RemotePeer
import org.whispersystems.signalservice.internal.push.SyncMessage.CallEvent

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

  @JvmStatic
  fun createObservedCallEvent(remotePeer: RemotePeer, timestamp: Long, isOutgoing: Boolean, isVideoCall: Boolean): CallEvent {
    return createCallEvent(
      remotePeer.id,
      remotePeer.callId.longValue(),
      timestamp,
      isOutgoing,
      isVideoCall,
      CallEvent.Event.OBSERVED
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
    val callType = when {
      recipient.isCallLink -> CallEvent.Type.AD_HOC_CALL
      recipient.isGroup -> CallEvent.Type.GROUP_CALL
      isVideoCall -> CallEvent.Type.VIDEO_CALL
      else -> CallEvent.Type.AUDIO_CALL
    }

    val conversationId: ByteString = when {
      recipient.isCallLink -> recipient.requireCallLinkRoomId().encodeForProto()
      recipient.isGroup -> recipient.requireGroupId().decodedId.toByteString()
      else -> recipient.requireServiceId().toByteString()
    }

    return CallEvent(
      conversationId = conversationId,
      id = callId,
      timestamp = timestamp,
      type = callType,
      direction = if (isOutgoing) CallEvent.Direction.OUTGOING else CallEvent.Direction.INCOMING,
      event = event
    )
  }
}
