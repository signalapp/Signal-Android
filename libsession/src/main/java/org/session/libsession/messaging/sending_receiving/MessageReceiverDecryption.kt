package org.session.libsession.messaging.sending_receiving

import org.session.libsession.messaging.Configuration
import org.session.libsession.messaging.sending_receiving.MessageReceiver.Error
import org.session.libsession.utilities.AESGCM

import org.whispersystems.curve25519.Curve25519

import org.session.libsignal.libsignal.loki.ClosedGroupCiphertextMessage
import org.session.libsignal.libsignal.util.Pair
import org.session.libsignal.service.api.crypto.SignalServiceCipher
import org.session.libsignal.service.api.messages.SignalServiceEnvelope
import org.session.libsignal.service.api.push.SignalServiceAddress
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.protocol.closedgroups.SharedSenderKeysImplementation
import org.session.libsignal.service.loki.utilities.toHexString

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object MessageReceiverDecryption {

    internal fun decryptWithSignalProtocol(envelope: SignalServiceProtos.Envelope): Pair<ByteArray, String> {
        val storage = Configuration.shared.signalStorage
        val sskDatabase = Configuration.shared.sskDatabase
        val sessionResetImp = Configuration.shared.sessionResetImp
        val certificateValidator = Configuration.shared.certificateValidator
        val data = envelope.content
        if (data.count() == 0) { throw Error.NoData }
        val userPublicKey = Configuration.shared.storage.getUserPublicKey() ?: throw Error.NoUserPublicKey
        val localAddress = SignalServiceAddress(userPublicKey)
        val cipher = SignalServiceCipher(localAddress, storage, sskDatabase, sessionResetImp, certificateValidator)
        val result = cipher.decrypt(SignalServiceEnvelope(envelope))
        return Pair(result, result.sender)
    }

    internal fun decryptWithSharedSenderKeys(envelope: SignalServiceProtos.Envelope): Pair<ByteArray, String> {
        // 1. ) Check preconditions
        val groupPublicKey = envelope.source
        if (!Configuration.shared.storage.isClosedGroup(groupPublicKey)) { throw Error.InvalidGroupPublicKey }
        val data = envelope.content
        if (data.count() == 0) { throw Error.NoData }
        val groupPrivateKey = Configuration.shared.storage.getClosedGroupPrivateKey(groupPublicKey) ?: throw Error.NoGroupPrivateKey
        // 2. ) Parse the wrapper
        val wrapper = SignalServiceProtos.ClosedGroupCiphertextMessageWrapper.parseFrom(data)
        val ivAndCiphertext = wrapper.ciphertext.toByteArray()
        val ephemeralPublicKey = wrapper.ephemeralPublicKey.toByteArray()
        // 3. ) Decrypt the data inside
        val ephemeralSharedSecret = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(ephemeralPublicKey, groupPrivateKey.serialize())
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("LOKI".toByteArray(), "HmacSHA256"))
        val symmetricKey = mac.doFinal(ephemeralSharedSecret)
        val closedGroupCiphertextMessageAsData = AESGCM.decrypt(ivAndCiphertext, symmetricKey)
        // 4. ) Parse the closed group ciphertext message
        val closedGroupCiphertextMessage = ClosedGroupCiphertextMessage.from(closedGroupCiphertextMessageAsData) ?: throw Error.ParsingFailed
        val senderPublicKey = closedGroupCiphertextMessage.senderPublicKey.toHexString()
        if (senderPublicKey == Configuration.shared.storage.getUserPublicKey()) { throw Error.SelfSend }
        // 5. ) Use the info inside the closed group ciphertext message to decrypt the actual message content
        val plaintext = SharedSenderKeysImplementation.shared.decrypt(closedGroupCiphertextMessage.ivAndCiphertext, groupPublicKey, senderPublicKey, closedGroupCiphertextMessage.keyIndex)
        // 6. ) Return
        return Pair(plaintext, senderPublicKey)
    }
}