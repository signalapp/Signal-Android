package org.thoughtcrime.securesms.messages

import org.signal.ringrtc.CallId
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.messages.MessageContentProcessor.Companion.log
import org.thoughtcrime.securesms.messages.MessageContentProcessor.Companion.warn
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
import org.whispersystems.signalservice.internal.push.CallMessage
import org.whispersystems.signalservice.internal.push.CallMessage.Offer
import org.whispersystems.signalservice.internal.push.CallMessage.Opaque
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.Envelope
import kotlin.time.Duration.Companion.milliseconds

object CallMessageProcessor {
  fun process(
    senderRecipient: Recipient,
    envelope: Envelope,
    content: Content,
    metadata: EnvelopeMetadata,
    serverDeliveredTimestamp: Long
  ) {
    val callMessage = content.callMessage!!

    when {
      callMessage.offer != null -> handleCallOfferMessage(envelope, metadata, callMessage.offer!!, senderRecipient.id, serverDeliveredTimestamp)
      callMessage.answer != null -> handleCallAnswerMessage(envelope, metadata, callMessage.answer!!, senderRecipient.id)
      callMessage.iceUpdate.isNotEmpty() -> handleCallIceUpdateMessage(envelope, metadata, callMessage.iceUpdate, senderRecipient.id)
      callMessage.hangup != null -> handleCallHangupMessage(envelope, metadata, callMessage.hangup!!, senderRecipient.id)
      callMessage.busy != null -> handleCallBusyMessage(envelope, metadata, callMessage.busy!!, senderRecipient.id)
      callMessage.opaque != null -> handleCallOpaqueMessage(envelope, metadata, callMessage.opaque!!, senderRecipient.requireAci(), serverDeliveredTimestamp)
    }
  }

  private fun handleCallOfferMessage(envelope: Envelope, metadata: EnvelopeMetadata, offer: Offer, senderRecipientId: RecipientId, serverDeliveredTimestamp: Long) {
    log(envelope.timestamp!!, "handleCallOfferMessage...")

    val offerId = if (offer.id != null && offer.type != null && offer.opaque != null) {
      offer.id!!
    } else {
      warn(envelope.timestamp!!, "Invalid offer, missing id, type, or opaque")
      return
    }

    val remotePeer = RemotePeer(senderRecipientId, CallId(offerId))
    val remoteIdentityKey = AppDependencies.protocolStore.aci().identities().getIdentityRecord(senderRecipientId).map { (_, identityKey): IdentityRecord -> identityKey.serialize() }.get()

    AppDependencies.signalCallManager
      .receivedOffer(
        CallMetadata(remotePeer, metadata.sourceDeviceId),
        OfferMetadata(offer.opaque?.toByteArray(), OfferMessage.Type.fromProto(offer.type!!)),
        ReceivedOfferMetadata(
          remoteIdentityKey,
          envelope.serverTimestamp!!,
          serverDeliveredTimestamp
        )
      )
  }

  private fun handleCallAnswerMessage(
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    answer: CallMessage.Answer,
    senderRecipientId: RecipientId
  ) {
    log(envelope.timestamp!!, "handleCallAnswerMessage...")

    val answerId = if (answer.id != null && answer.opaque != null) {
      answer.id!!
    } else {
      warn(envelope.timestamp!!, "Invalid answer, missing id or opaque")
      return
    }

    val remotePeer = RemotePeer(senderRecipientId, CallId(answerId))
    val remoteIdentityKey = AppDependencies.protocolStore.aci().identities().getIdentityRecord(senderRecipientId).map { (_, identityKey): IdentityRecord -> identityKey.serialize() }.get()

    AppDependencies.signalCallManager
      .receivedAnswer(
        CallMetadata(remotePeer, metadata.sourceDeviceId),
        AnswerMetadata(answer.opaque?.toByteArray()),
        ReceivedAnswerMetadata(remoteIdentityKey)
      )
  }

  private fun handleCallIceUpdateMessage(
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    iceUpdateList: List<CallMessage.IceUpdate>,
    senderRecipientId: RecipientId
  ) {
    log(envelope.timestamp!!, "handleCallIceUpdateMessage... " + iceUpdateList.size)

    val iceCandidates: MutableList<ByteArray> = ArrayList(iceUpdateList.size)
    var callId: Long = -1

    iceUpdateList
      .filter { it.opaque != null && it.id != null }
      .forEach { iceUpdate ->
        iceCandidates += iceUpdate.opaque!!.toByteArray()
        callId = iceUpdate.id!!
      }

    if (iceCandidates.isNotEmpty()) {
      val remotePeer = RemotePeer(senderRecipientId, CallId(callId))
      AppDependencies.signalCallManager
        .receivedIceCandidates(
          CallMetadata(remotePeer, metadata.sourceDeviceId),
          iceCandidates
        )
    } else {
      warn(envelope.timestamp!!, "Invalid ice updates, all missing opaque and/or call id")
    }
  }

  private fun handleCallHangupMessage(
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    hangup: CallMessage.Hangup?,
    senderRecipientId: RecipientId
  ) {
    log(envelope.timestamp!!, "handleCallHangupMessage")

    val (hangupId: Long, hangupDeviceId: Int?) = if (hangup?.id != null) {
      hangup.id!! to hangup.deviceId
    } else {
      warn(envelope.timestamp!!, "Invalid hangup, null message or missing id/deviceId")
      return
    }

    val remotePeer = RemotePeer(senderRecipientId, CallId(hangupId))
    AppDependencies.signalCallManager
      .receivedCallHangup(
        CallMetadata(remotePeer, metadata.sourceDeviceId),
        HangupMetadata(HangupMessage.Type.fromProto(hangup.type), hangupDeviceId ?: 0)
      )
  }

  private fun handleCallBusyMessage(envelope: Envelope, metadata: EnvelopeMetadata, busy: CallMessage.Busy, senderRecipientId: RecipientId) {
    log(envelope.timestamp!!, "handleCallBusyMessage")

    val busyId = if (busy.id != null) {
      busy.id!!
    } else {
      warn(envelope.timestamp!!, "Invalid busy, missing call id")
      return
    }

    val remotePeer = RemotePeer(senderRecipientId, CallId(busyId))
    AppDependencies.signalCallManager.receivedCallBusy(CallMetadata(remotePeer, metadata.sourceDeviceId))
  }

  private fun handleCallOpaqueMessage(envelope: Envelope, metadata: EnvelopeMetadata, opaque: Opaque, senderServiceId: ServiceId, serverDeliveredTimestamp: Long) {
    log(envelope.timestamp!!, "handleCallOpaqueMessage")

    val data = if (opaque.data_ != null) {
      opaque.data_!!.toByteArray()
    } else {
      warn(envelope.timestamp!!, "Invalid opaque message, null data")
      return
    }

    var messageAgeSeconds: Long = 0
    if (envelope.serverTimestamp in 1..serverDeliveredTimestamp) {
      messageAgeSeconds = (serverDeliveredTimestamp - envelope.serverTimestamp!!).milliseconds.inWholeSeconds
    }

    AppDependencies.signalCallManager
      .receivedOpaqueMessage(
        OpaqueMessageMetadata(
          senderServiceId.rawUuid,
          data,
          metadata.sourceDeviceId,
          messageAgeSeconds
        )
      )
  }
}
