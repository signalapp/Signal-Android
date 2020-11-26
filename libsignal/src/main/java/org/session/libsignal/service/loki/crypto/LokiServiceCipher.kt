package org.session.libsignal.service.loki.crypto

import org.session.libsignal.metadata.certificate.CertificateValidator
import org.session.libsignal.libsignal.InvalidMessageException
import org.session.libsignal.libsignal.loki.FallbackSessionCipher
import org.session.libsignal.libsignal.loki.SessionResetProtocol
import org.session.libsignal.libsignal.state.SignalProtocolStore
import org.session.libsignal.service.api.crypto.SignalServiceCipher
import org.session.libsignal.service.api.messages.SignalServiceEnvelope
import org.session.libsignal.service.api.push.SignalServiceAddress
import org.session.libsignal.service.internal.push.PushTransportDetails
import org.session.libsignal.service.loki.protocol.closedgroups.SharedSenderKeysDatabaseProtocol

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
