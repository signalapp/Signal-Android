package org.whispersystems.signalservice.loki.crypto

import org.signal.libsignal.metadata.certificate.CertificateValidator
import org.whispersystems.libsignal.InvalidMessageException
import org.whispersystems.libsignal.loki.FallbackSessionCipher
import org.whispersystems.libsignal.loki.SessionResetProtocol
import org.whispersystems.libsignal.state.SignalProtocolStore
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.PushTransportDetails
import org.whispersystems.signalservice.loki.protocol.closedgroups.SharedSenderKeysDatabaseProtocol

class LokiServiceCipher(localAddress: SignalServiceAddress, private val signalProtocolStore: SignalProtocolStore, private val sskDatabase: SharedSenderKeysDatabaseProtocol, sessionResetProtocol: SessionResetProtocol, certificateValidator: CertificateValidator?) : SignalServiceCipher(localAddress, signalProtocolStore, sskDatabase, sessionResetProtocol, certificateValidator) {

    private val userPrivateKey get() = signalProtocolStore.identityKeyPair.privateKey.serialize()

    override fun decrypt(envelope: SignalServiceEnvelope, ciphertext: ByteArray): Plaintext {
        return if (envelope.isFallbackMessage) decryptFallbackMessage(envelope, ciphertext) else super.decrypt(envelope, ciphertext)
    }

    private fun decryptFallbackMessage(envelope: SignalServiceEnvelope, ciphertext: ByteArray): Plaintext {
        val cipher = FallbackSessionCipher(userPrivateKey, envelope.source)
        val paddedMessageBody = cipher.decrypt(ciphertext) ?: throw InvalidMessageException("Failed to decrypt fallback message.")
        val transportDetails = PushTransportDetails(FallbackSessionCipher.sessionVersion)
        val unpaddedMessageBody = transportDetails.getStrippedPaddingMessageBody(paddedMessageBody)
        val metadata = Metadata(envelope.source, envelope.sourceDevice, envelope.timestamp, false)
        return Plaintext(metadata, unpaddedMessageBody)
    }
}
