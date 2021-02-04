package org.session.libsignal.service.loki.protocol.closedgroups

import com.google.protobuf.ByteString
import org.whispersystems.curve25519.Curve25519
import org.session.libsignal.libsignal.loki.ClosedGroupCiphertextMessage
import org.session.libsignal.utilities.Hex
import org.session.libsignal.libsignal.util.Pair
import org.session.libsignal.service.api.messages.SignalServiceEnvelope
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.api.utilities.DecryptionUtilities
import org.session.libsignal.service.loki.api.utilities.EncryptionUtilities
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded
import org.session.libsignal.service.loki.utilities.toHexString
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

public object ClosedGroupUtilities {

    sealed class Error(val description: String) : Exception() {
        object InvalidGroupPublicKey : Error("Invalid group public key.")
        object NoData : Error("Received an empty envelope.")
        object NoGroupPrivateKey : Error("Missing group private key.")
        object ParsingFailed : Error("Couldn't parse closed group ciphertext message.")
    }

    @JvmStatic
    public fun encrypt(data: ByteArray, groupPublicKey: String, userPublicKey: String): ByteArray {
        // 1. ) Encrypt the data with the user's sender key
        val ciphertextAndKeyIndex = SharedSenderKeysImplementation.shared.encrypt(data, groupPublicKey, userPublicKey)
        val ivAndCiphertext = ciphertextAndKeyIndex.first
        val keyIndex = ciphertextAndKeyIndex.second
        val x0 = ClosedGroupCiphertextMessage(ivAndCiphertext, Hex.fromStringCondensed(userPublicKey), keyIndex);
        // 2. ) Encrypt the result for the group's public key to hide the sender public key and key index
        val x1 = EncryptionUtilities.encryptForX25519PublicKey(x0.serialize(), groupPublicKey.removing05PrefixIfNeeded())
        // 3. ) Wrap the result
        return SignalServiceProtos.ClosedGroupCiphertextMessageWrapper.newBuilder()
            .setCiphertext(ByteString.copyFrom(x1.ciphertext))
            .setEphemeralPublicKey(ByteString.copyFrom(x1.ephemeralPublicKey))
            .build().toByteArray()
    }

    @JvmStatic
    public fun decrypt(envelope: SignalServiceEnvelope): Pair<ByteArray, String> {
        // 1. ) Check preconditions
        val groupPublicKey = envelope.source
        if (groupPublicKey == null || !SharedSenderKeysImplementation.shared.isClosedGroup(groupPublicKey)) {
            throw Error.InvalidGroupPublicKey
        }
        val data = envelope.content
        if (data.count() == 0) {
            throw Error.NoData
        }
        val groupPrivateKey = SharedSenderKeysImplementation.shared.getKeyPair(groupPublicKey)?.privateKey?.serialize()
        if (groupPrivateKey == null) {
            throw Error.NoGroupPrivateKey
        }
        // 2. ) Parse the wrapper
        val x0 = SignalServiceProtos.ClosedGroupCiphertextMessageWrapper.parseFrom(data)
        val ivAndCiphertext = x0.ciphertext.toByteArray()
        val ephemeralPublicKey = x0.ephemeralPublicKey.toByteArray()
        // 3. ) Decrypt the data inside
        val ephemeralSharedSecret = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(ephemeralPublicKey, groupPrivateKey)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("LOKI".toByteArray(), "HmacSHA256"))
        val symmetricKey = mac.doFinal(ephemeralSharedSecret)
        val x1 = DecryptionUtilities.decryptUsingAESGCM(ivAndCiphertext, symmetricKey)
        // 4. ) Parse the closed group ciphertext message
        val x2 = ClosedGroupCiphertextMessage.from(x1)
        if (x2 == null) {
            throw Error.ParsingFailed
        }
        val senderPublicKey = x2.senderPublicKey.toHexString()
        // 5. ) Use the info inside the closed group ciphertext message to decrypt the actual message content
        val plaintext = SharedSenderKeysImplementation.shared.decrypt(x2.ivAndCiphertext, groupPublicKey, senderPublicKey, x2.keyIndex)
        // 6. ) Return
        return Pair(plaintext, senderPublicKey)
    }
}
