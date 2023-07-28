package org.thoughtcrime.securesms.messages

import org.signal.ringrtc.CallId
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.messages.MessageContentProcessorV2.Companion.log
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.ringrtc.RemotePeer
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.AnswerMetadata
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.CallMetadata
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.HangupMetadata
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.OfferMetadata
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.OpaqueMessageMetadata
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.ReceivedAnswerMetadata
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.ReceivedOfferMetadata
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.api.messages.calls.HangupMessage
import org.whispersystems.signalservice.api.messages.calls.OfferMessage
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.CallMessage.Offer
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.CallMessage.Opaque
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Envelope

object CallMessageProcessor {
  fun process(
    senderRecipient: Recipient,
    envelope: Envelope,
    content: SignalServiceProtos.Content,
    metadata: EnvelopeMetadata,
    serverDeliveredTimestamp: Long
  ) {
    val callMessage = content.callMessage

    when {
      callMessage.hasOffer() -> handleCallOfferMessage(envelope, metadata, callMessage.offer, senderRecipient.id, serverDeliveredTimestamp, callMessage.multiRing)
      callMessage.hasAnswer() -> handleCallAnswerMessage(envelope, metadata, callMessage.answer, senderRecipient.id, callMessage.multiRing)
      callMessage.iceUpdateList.isNotEmpty() -> handleCallIceUpdateMessage(envelope, metadata, callMessage.iceUpdateList, senderRecipient.id)
      callMessage.hasHangup() || callMessage.hasLegacyHangup() -> {
        val hangup = if (callMessage.hasHangup()) callMessage.hangup else callMessage.legacyHangup
        handleCallHangupMessage(envelope, metadata, hangup, senderRecipient.id, callMessage.hasLegacyHangup())
      }
      callMessage.hasBusy() -> handleCallBusyMessage(envelope, metadata, callMessage.busy, senderRecipient.id)
      callMessage.hasOpaque() -> handleCallOpaqueMessage(envelope, metadata, callMessage.opaque, senderRecipient.requireAci(), serverDeliveredTimestamp)
    }
  }

  private fun handleCallOfferMessage(envelope: Envelope, metadata: EnvelopeMetadata, offer: Offer, senderRecipientId: RecipientId, serverDeliveredTimestamp: Long, multiRing: Boolean) {
    log(envelope.timestamp, "handleCallOfferMessage...")

    val remotePeer = RemotePeer(senderRecipientId, CallId(offer.id))
    val remoteIdentityKey = ApplicationDependencies.getProtocolStore().aci().identities().getIdentityRecord(senderRecipientId).map { (_, identityKey): IdentityRecord -> identityKey.serialize() }.get()

    ApplicationDependencies.getSignalCallManager()
      .receivedOffer(
        CallMetadata(remotePeer, metadata.sourceDeviceId),
        offer.toCallOfferMetadata(),
        ReceivedOfferMetadata(
          remoteIdentityKey,
          envelope.serverTimestamp,
          serverDeliveredTimestamp,
          multiRing
        )
      )
  }

  private fun handleCallAnswerMessage(
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    answer: SignalServiceProtos.CallMessage.Answer,
    senderRecipientId: RecipientId,
    multiRing: Boolean
  ) {
    log(envelope.timestamp, "handleCallAnswerMessage...")

    val remotePeer = RemotePeer(senderRecipientId, CallId(answer.id))
    val remoteIdentityKey = ApplicationDependencies.getProtocolStore().aci().identities().getIdentityRecord(senderRecipientId).map { (_, identityKey): IdentityRecord -> identityKey.serialize() }.get()

    ApplicationDependencies.getSignalCallManager()
      .receivedAnswer(
        CallMetadata(remotePeer, metadata.sourceDeviceId),
        AnswerMetadata(if (answer.hasOpaque()) answer.opaque.toByteArray() else null, if (answer.hasSdp()) answer.sdp else null),
        ReceivedAnswerMetadata(remoteIdentityKey, multiRing)
      )
  }

  private fun handleCallIceUpdateMessage(
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    iceUpdateList: MutableList<SignalServiceProtos.CallMessage.IceUpdate>,
    senderRecipientId: RecipientId
  ) {
    log(envelope.timestamp, "handleCallIceUpdateMessage... " + iceUpdateList.size)

    val iceCandidates: MutableList<ByteArray> = ArrayList(iceUpdateList.size)
    var callId: Long = -1

    iceUpdateList
      .filter { it.hasOpaque() }
      .forEach { iceUpdate ->
        iceCandidates += iceUpdate.opaque.toByteArray()
        callId = iceUpdate.id
      }

    val remotePeer = RemotePeer(senderRecipientId, CallId(callId))
    ApplicationDependencies.getSignalCallManager()
      .receivedIceCandidates(
        CallMetadata(remotePeer, metadata.sourceDeviceId),
        iceCandidates
      )
  }

  private fun handleCallHangupMessage(
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    hangup: SignalServiceProtos.CallMessage.Hangup,
    senderRecipientId: RecipientId,
    isLegacyHangup: Boolean
  ) {
    log(envelope.timestamp, "handleCallHangupMessage")

    val remotePeer = RemotePeer(senderRecipientId, CallId(hangup.id))
    ApplicationDependencies.getSignalCallManager()
      .receivedCallHangup(
        CallMetadata(remotePeer, metadata.sourceDeviceId),
        HangupMetadata(HangupMessage.Type.fromProto(hangup.type), isLegacyHangup, hangup.deviceId)
      )
  }

  private fun handleCallBusyMessage(envelope: Envelope, metadata: EnvelopeMetadata, busy: SignalServiceProtos.CallMessage.Busy, senderRecipientId: RecipientId) {
    log(envelope.timestamp, "handleCallBusyMessage")

    val remotePeer = RemotePeer(senderRecipientId, CallId(busy.id))
    ApplicationDependencies.getSignalCallManager().receivedCallBusy(CallMetadata(remotePeer, metadata.sourceDeviceId))
  }

  private fun handleCallOpaqueMessage(envelope: Envelope, metadata: EnvelopeMetadata, opaque: Opaque, senderServiceId: ServiceId, serverDeliveredTimestamp: Long) {
    log(envelope.timestamp.toString(), "handleCallOpaqueMessage")

    var messageAgeSeconds: Long = 0
    if (envelope.serverTimestamp in 1..serverDeliveredTimestamp) {
      messageAgeSeconds = (serverDeliveredTimestamp - envelope.serverTimestamp) / 1000
    }

    ApplicationDependencies.getSignalCallManager()
      .receivedOpaqueMessage(
        OpaqueMessageMetadata(
          senderServiceId.rawUuid,
          opaque.data.toByteArray(),
          metadata.sourceDeviceId,
          messageAgeSeconds
        )
      )
  }

  private fun Offer.toCallOfferMetadata(): OfferMetadata {
    val sdp = if (hasSdp()) sdp else null
    val opaque = if (hasOpaque()) opaque else null
    return OfferMetadata(opaque?.toByteArray(), sdp, OfferMessage.Type.fromProto(type))
  }
}
