package org.thoughtcrime.securesms.messages

import org.signal.ringrtc.CallId
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.messages.MessageContentProcessor.Companion.log
import org.thoughtcrime.securesms.messages.MessageContentProcessor.Companion.warn
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.ringrtc.RemotePeer
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.*
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.api.messages.calls.HangupMessage
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.internal.push.CallMessage
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.Envelope
import kotlin.time.Duration.Companion.milliseconds

interface CallHandler {
  fun canHandle(callMessage: CallMessage): Boolean
  fun handle(
    senderRecipient: Recipient,
    envelope: Envelope,
    content: Content,
    metadata: EnvelopeMetadata,
    serverDeliveredTimestamp: Long
  )
}

class CallMessageProcessor(private val handlers: List<CallHandler>) {
  fun process(
    senderRecipient: Recipient,
    envelope: Envelope,
    content: Content,
    metadata: EnvelopeMetadata,
    serverDeliveredTimestamp: Long
  ) {
    val callMessage = content.callMessage ?: return
    handlers.firstOrNull { it.canHandle(callMessage) }
      ?.handle(senderRecipient, envelope, content, metadata, serverDeliveredTimestamp)
      ?: warn(envelope.timestamp ?: 0, "No handler for call message type")
  }
}

class OfferCallHandler : CallHandler {
  override fun canHandle(callMessage: CallMessage): Boolean = callMessage.offer != null

  override fun handle(
    senderRecipient: Recipient,
    envelope: Envelope,
    content: Content,
    metadata: EnvelopeMetadata,
    serverDeliveredTimestamp: Long
  ) {
    log(envelope.timestamp ?: 0, "handleCallOfferMessage...")

    val offer = content.callMessage!!.offer!!
    if (offer.id == null || offer.type == null || offer.opaque == null) {
      warn(envelope.timestamp ?: 0, "Invalid offer, missing id, type, or opaque")
      return
    }

    val remotePeer = RemotePeer(senderRecipient.id, CallId(offer.id))
    val remoteIdentityKey = AppDependencies.protocolStore.aci().identities().getIdentityRecord(senderRecipient.id)
      .map { (_, identityKey): IdentityRecord -> identityKey.serialize() }.get()

    AppDependencies.signalCallManager.receivedOffer(
      CallMetadata(remotePeer, metadata.sourceDeviceId),
      OfferMetadata(offer.opaque.toByteArray(), CallMessage.Offer.Type.fromProto(offer.type)),
      ReceivedOfferMetadata(remoteIdentityKey, envelope.serverTimestamp!!, serverDeliveredTimestamp)
    )
  }
}

class AnswerCallHandler : CallHandler {
  override fun canHandle(callMessage: CallMessage): Boolean = callMessage.answer != null

  override fun handle(
    senderRecipient: Recipient,
    envelope: Envelope,
    content: Content,
    metadata: EnvelopeMetadata,
    serverDeliveredTimestamp: Long
  ) {
    log(envelope.timestamp ?: 0, "handleCallAnswerMessage...")

    val answer = content.callMessage!!.answer!!
    if (answer.id == null || answer.opaque == null) {
      warn(envelope.timestamp ?: 0, "Invalid answer, missing id or opaque")
      return
    }

    val remotePeer = RemotePeer(senderRecipient.id, CallId(answer.id))
    val remoteIdentityKey = AppDependencies.protocolStore.aci().identities().getIdentityRecord(senderRecipient.id)
      .map { (_, identityKey): IdentityRecord -> identityKey.serialize() }.get()

    AppDependencies.signalCallManager.receivedAnswer(
      CallMetadata(remotePeer, metadata.sourceDeviceId),
      AnswerMetadata(answer.opaque.toByteArray()),
      ReceivedAnswerMetadata(remoteIdentityKey)
    )
  }
}

class IceUpdateCallHandler : CallHandler {
  override fun canHandle(callMessage: CallMessage): Boolean = callMessage.iceUpdate.isNotEmpty()

  override fun handle(
    senderRecipient: Recipient,
    envelope: Envelope,
    content: Content,
    metadata: EnvelopeMetadata,
    serverDeliveredTimestamp: Long
  ) {
    log(envelope.timestamp ?: 0, "handleCallIceUpdateMessage... ${content.callMessage!!.iceUpdate.size}")

    val iceCandidates = content.callMessage!!.iceUpdate
      .filter { it.opaque != null && it.id != null }
      .map { it.opaque!!.toByteArray() }

    val callId = content.callMessage!!.iceUpdate.firstOrNull { it.id != null }?.id ?: -1L

    if (iceCandidates.isNotEmpty()) {
      val remotePeer = RemotePeer(senderRecipient.id, CallId(callId))
      AppDependencies.signalCallManager.receivedIceCandidates(
        CallMetadata(remotePeer, metadata.sourceDeviceId),
        iceCandidates
      )
    } else {
      warn(envelope.timestamp ?: 0, "Invalid ice updates, all missing opaque and/or call id")
    }
  }
}

class HangupCallHandler : CallHandler {
  override fun canHandle(callMessage: CallMessage): Boolean = callMessage.hangup != null

  override fun handle(
    senderRecipient: Recipient,
    envelope: Envelope,
    content: Content,
    metadata: EnvelopeMetadata,
    serverDeliveredTimestamp: Long
  ) {
    log(envelope.timestamp ?: 0, "handleCallHangupMessage")

    val hangup = content.callMessage!!.hangup
    if (hangup?.id == null) {
      warn(envelope.timestamp ?: 0, "Invalid hangup, null message or missing id/deviceId")
      return
    }

    val remotePeer = RemotePeer(senderRecipient.id, CallId(hangup.id))
    AppDependencies.signalCallManager.receivedCallHangup(
      CallMetadata(remotePeer, metadata.sourceDeviceId),
      HangupMetadata(HangupMessage.Type.fromProto(hangup.type), hangup.deviceId ?: 0)
    )
  }
}

class BusyCallHandler : CallHandler {
  override fun canHandle(callMessage: CallMessage): Boolean = callMessage.busy != null

  override fun handle(
    senderRecipient: Recipient,
    envelope: Envelope,
    content: Content,
    metadata: EnvelopeMetadata,
    serverDeliveredTimestamp: Long
  ) {
    log(envelope.timestamp ?: 0, "handleCallBusyMessage")

    val busy = content.callMessage!!.busy
    if (busy?.id == null) {
      warn(envelope.timestamp ?: 0, "Invalid busy, missing call id")
      return
    }

    val remotePeer = RemotePeer(senderRecipient.id, CallId(busy.id))
    AppDependencies.signalCallManager.receivedCallBusy(CallMetadata(remotePeer, metadata.sourceDeviceId))
  }
}

class OpaqueCallHandler : CallHandler {
  override fun canHandle(callMessage: CallMessage): Boolean = callMessage.opaque != null

  override fun handle(
    senderRecipient: Recipient,
    envelope: Envelope,
    content: Content,
    metadata: EnvelopeMetadata,
    serverDeliveredTimestamp: Long
  ) {
    log(envelope.timestamp ?: 0, "handleCallOpaqueMessage")

    val opaque = content.callMessage!!.opaque
    val data = opaque?.data_?.toByteArray() ?: run {
      warn(envelope.timestamp ?: 0, "Invalid opaque message, null data")
      return
    }

    val messageAgeSeconds = if (
      envelope.serverTimestamp in 1..serverDeliveredTimestamp
    ) {
      (serverDeliveredTimestamp - envelope.serverTimestamp!!).milliseconds.inWholeSeconds
    } else 0

    AppDependencies.signalCallManager.receivedOpaqueMessage(
      OpaqueMessageMetadata(
        senderRecipient.requireAci().rawUuid,
        data,
        metadata.sourceDeviceId,
        messageAgeSeconds
      )
    )
  }
}
