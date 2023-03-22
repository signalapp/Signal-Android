package org.thoughtcrime.securesms.testing

import org.signal.libsignal.internal.Native
import org.signal.libsignal.internal.NativeHandleGuard
import org.signal.libsignal.metadata.certificate.CertificateValidator
import org.signal.libsignal.metadata.certificate.SenderCertificate
import org.signal.libsignal.metadata.certificate.ServerCertificate
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.database.model.toProtoByteString
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Envelope
import org.whispersystems.util.Base64
import java.util.Optional
import java.util.UUID

object FakeClientHelpers {

  val noOpCertificateValidator = object : CertificateValidator(null) {
    override fun validate(certificate: SenderCertificate, validationTime: Long) = Unit
  }

  fun createCertificateFor(trustRoot: ECKeyPair, uuid: UUID, e164: String, deviceId: Int, identityKey: ECPublicKey, expires: Long): SenderCertificate {
    val serverKey: ECKeyPair = Curve.generateKeyPair()
    NativeHandleGuard(serverKey.publicKey).use { serverPublicGuard ->
      NativeHandleGuard(trustRoot.privateKey).use { trustRootPrivateGuard ->
        val serverCertificate = ServerCertificate(Native.ServerCertificate_New(1, serverPublicGuard.nativeHandle(), trustRootPrivateGuard.nativeHandle()))
        NativeHandleGuard(identityKey).use { identityGuard ->
          NativeHandleGuard(serverCertificate).use { serverCertificateGuard ->
            NativeHandleGuard(serverKey.privateKey).use { serverPrivateGuard ->
              return SenderCertificate(Native.SenderCertificate_New(uuid.toString(), e164, deviceId, identityGuard.nativeHandle(), expires, serverCertificateGuard.nativeHandle(), serverPrivateGuard.nativeHandle()))
            }
          }
        }
      }
    }
  }

  fun getTargetUnidentifiedAccess(myProfileKey: ProfileKey, theirProfileKey: ProfileKey, senderCertificate: SenderCertificate): Optional<UnidentifiedAccess> {
    val selfUnidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(myProfileKey)
    val themUnidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(theirProfileKey)

    return UnidentifiedAccessPair(UnidentifiedAccess(selfUnidentifiedAccessKey, senderCertificate.serialized), UnidentifiedAccess(themUnidentifiedAccessKey, senderCertificate.serialized)).targetUnidentifiedAccess
  }

  fun encryptedTextMessage(now: Long, message: String = "Test body message"): EnvelopeContent {
    val content = SignalServiceProtos.Content.newBuilder().apply {
      setDataMessage(
        SignalServiceProtos.DataMessage.newBuilder().apply {
          body = message
          timestamp = now
        }
      )
    }
    return EnvelopeContent.encrypted(content.build(), ContentHint.RESENDABLE, Optional.empty())
  }

  fun OutgoingPushMessage.toEnvelope(timestamp: Long, destination: ServiceId): Envelope {
    return Envelope.newBuilder()
      .setType(Envelope.Type.valueOf(this.type))
      .setSourceDevice(1)
      .setTimestamp(timestamp)
      .setServerTimestamp(timestamp + 1)
      .setDestinationUuid(destination.toString())
      .setServerGuid(UUID.randomUUID().toString())
      .setContent(Base64.decode(this.content).toProtoByteString())
      .setUrgent(true)
      .setStory(false)
      .build()
  }
}
