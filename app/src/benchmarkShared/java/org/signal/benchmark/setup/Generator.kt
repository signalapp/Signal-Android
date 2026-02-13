package org.signal.benchmark.setup

import okio.ByteString.Companion.toByteString
import org.signal.core.models.ServiceId
import org.signal.core.util.Base64
import org.signal.core.util.toByteArray
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.buildWith
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage
import java.util.Optional
import java.util.UUID

object Generator {

  fun encryptedTextMessage(now: Long, message: String = "Test body message"): EnvelopeContent {
    val content = Content.Builder().apply {
      dataMessage(
        DataMessage.Builder().buildWith {
          body = message
          timestamp = now
        }
      )
    }
    return EnvelopeContent.encrypted(content.build(), ContentHint.RESENDABLE, Optional.empty())
  }

  fun OutgoingPushMessage.toEnvelope(timestamp: Long, destination: ServiceId): Envelope {
    val serverGuid = UUID.randomUUID()
    return Envelope.Builder()
      .type(Envelope.Type.fromValue(this.type))
      .sourceDevice(1)
      .timestamp(timestamp)
      .serverTimestamp(timestamp + 1)
      .destinationServiceId(destination.toString())
      .destinationServiceIdBinary(destination.toByteString())
      .serverGuid(serverGuid.toString())
      .serverGuidBinary(serverGuid.toByteArray().toByteString())
      .content(Base64.decode(this.content).toByteString())
      .urgent(true)
      .story(false)
      .build()
  }
}
