package org.thoughtcrime.securesms.testing

import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64
import org.signal.libsignal.metadata.certificate.CertificateValidator
import org.signal.libsignal.metadata.certificate.SenderCertificate
import org.signal.libsignal.metadata.certificate.ServerCertificate
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.buildWith
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage
import java.util.Optional
import java.util.UUID

object FakeClientHelpers {

  val noOpCertificateValidator = object : CertificateValidator(ECKeyPair.generate().publicKey) {
    override fun validate(certificate: SenderCertificate, validationTime: Long) = Unit
  }

  fun createCertificateFor(trustRoot: ECKeyPair, uuid: UUID, e164: String, deviceId: Int, identityKey: ECPublicKey, expires: Long): SenderCertificate {
    val serverKey: ECKeyPair = ECKeyPair.generate()
    val serverCertificate = ServerCertificate(trustRoot.privateKey, 1, serverKey.publicKey)
    return serverCertificate.issue(serverKey.privateKey, uuid.toString(), Optional.of(e164), deviceId, identityKey, expires)
  }

  fun getSealedSenderAccess(theirProfileKey: ProfileKey, senderCertificate: SenderCertificate): SealedSenderAccess? {
    val themUnidentifiedAccessKey = UnidentifiedAccess(UnidentifiedAccess.deriveAccessKeyFrom(theirProfileKey), senderCertificate.serialized, false)

    return SealedSenderAccess.forIndividual(themUnidentifiedAccessKey)
  }

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
    return Envelope.Builder()
      .type(Envelope.Type.fromValue(this.type))
      .sourceDevice(1)
      .timestamp(timestamp)
      .serverTimestamp(timestamp + 1)
      .destinationServiceId(destination.toString())
      .serverGuid(UUID.randomUUID().toString())
      .content(Base64.decode(this.content).toByteString())
      .urgent(true)
      .story(false)
      .build()
  }
}
