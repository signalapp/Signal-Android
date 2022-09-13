package org.session.libsession.messaging.sending_receiving

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.interfaces.Sign
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.sending_receiving.MessageReceiver.Error
import org.session.libsession.messaging.utilities.SessionId
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.hexEncodedPublicKey
import org.session.libsignal.utilities.removingIdPrefixIfNeeded

object MessageDecrypter {

    private val sodium by lazy { LazySodiumAndroid(SodiumAndroid()) }

    /**
     * Decrypts `ciphertext` using the Session protocol and `x25519KeyPair`.
     *
     * @param ciphertext the data to decrypt.
     * @param x25519KeyPair the key pair to use for decryption. This could be the current user's key pair, or the key pair of a closed group.
     *
     * @return the padded plaintext.
     */
    public fun decrypt(ciphertext: ByteArray, x25519KeyPair: ECKeyPair): Pair<ByteArray, String> {
        val recipientX25519PrivateKey = x25519KeyPair.privateKey.serialize()
        val recipientX25519PublicKey = Hex.fromStringCondensed(x25519KeyPair.hexEncodedPublicKey.removingIdPrefixIfNeeded())
        val signatureSize = Sign.BYTES
        val ed25519PublicKeySize = Sign.PUBLICKEYBYTES

        // 1. ) Decrypt the message
        val plaintextWithMetadata = ByteArray(ciphertext.size - Box.SEALBYTES)
        try {
            sodium.cryptoBoxSealOpen(plaintextWithMetadata, ciphertext, ciphertext.size.toLong(), recipientX25519PublicKey, recipientX25519PrivateKey)
        } catch (exception: Exception) {
            Log.d("Loki", "Couldn't decrypt message due to error: $exception.")
            throw Error.DecryptionFailed
        }
        if (plaintextWithMetadata.size <= (signatureSize + ed25519PublicKeySize)) { throw Error.DecryptionFailed }
        // 2. ) Get the message parts
        val signature = plaintextWithMetadata.sliceArray(plaintextWithMetadata.size - signatureSize until plaintextWithMetadata.size)
        val senderED25519PublicKey = plaintextWithMetadata.sliceArray(plaintextWithMetadata.size - (signatureSize + ed25519PublicKeySize) until plaintextWithMetadata.size - signatureSize)
        val plaintext = plaintextWithMetadata.sliceArray(0 until plaintextWithMetadata.size - (signatureSize + ed25519PublicKeySize))
        // 3. ) Verify the signature
        val verificationData = (plaintext + senderED25519PublicKey + recipientX25519PublicKey)
        try {
            val isValid = sodium.cryptoSignVerifyDetached(signature, verificationData, verificationData.size, senderED25519PublicKey)
            if (!isValid) { throw Error.InvalidSignature }
        } catch (exception: Exception) {
            Log.d("Loki", "Couldn't verify message signature due to error: $exception.")
            throw Error.InvalidSignature
        }
        // 4. ) Get the sender's X25519 public key
        val senderX25519PublicKey = ByteArray(Sign.CURVE25519_PUBLICKEYBYTES)
        sodium.convertPublicKeyEd25519ToCurve25519(senderX25519PublicKey, senderED25519PublicKey)

        val id = SessionId(IdPrefix.STANDARD, senderX25519PublicKey)
        return Pair(plaintext, id.hexString)
    }

    fun decryptBlinded(
        message: ByteArray,
        isOutgoing: Boolean,
        otherBlindedPublicKey: String,
        serverPublicKey: String
    ): Pair<ByteArray, String> {
        if (message.size < Box.NONCEBYTES + 2) throw Error.DecryptionFailed
        val userEdKeyPair = MessagingModuleConfiguration.shared.getUserED25519KeyPair() ?: throw Error.NoUserED25519KeyPair
        val blindedKeyPair = SodiumUtilities.blindedKeyPair(serverPublicKey, userEdKeyPair) ?: throw Error.DecryptionFailed
        // Calculate the shared encryption key, receiving from A to B
        val otherKeyBytes = Hex.fromStringCondensed(otherBlindedPublicKey.removingIdPrefixIfNeeded())
        val kA = if (isOutgoing) blindedKeyPair.publicKey.asBytes else otherKeyBytes
        val decryptionKey = SodiumUtilities.sharedBlindedEncryptionKey(
            userEdKeyPair.secretKey.asBytes,
            otherKeyBytes,
            kA,
            if (isOutgoing) otherKeyBytes else blindedKeyPair.publicKey.asBytes
        ) ?: throw Error.DecryptionFailed

        // v, ct, nc = data[0], data[1:-24], data[-24:size]
        val version = message.first().toInt()
        if (version != 0) throw Error.DecryptionFailed
        val ciphertext = message.drop(1).dropLast(Box.NONCEBYTES).toByteArray()
        val nonce = message.takeLast(Box.NONCEBYTES).toByteArray()

        // Decrypt the message
        val innerBytes = SodiumUtilities.decrypt(ciphertext, decryptionKey, nonce) ?: throw Error.DecryptionFailed
        if (innerBytes.size < Sign.PUBLICKEYBYTES) throw Error.DecryptionFailed

        // Split up: the last 32 bytes are the sender's *unblinded* ed25519 key
        val plaintextEndIndex = innerBytes.size - Sign.PUBLICKEYBYTES
        val plaintext = innerBytes.slice(0 until plaintextEndIndex).toByteArray()
        val senderEdPublicKey = innerBytes.slice((plaintextEndIndex until innerBytes.size)).toByteArray()

        // Verify that the inner senderEdPublicKey (A) yields the same outer kA we got with the message
        val blindingFactor = SodiumUtilities.generateBlindingFactor(serverPublicKey) ?: throw Error.DecryptionFailed
        val sharedSecret = SodiumUtilities.combineKeys(blindingFactor, senderEdPublicKey) ?: throw Error.DecryptionFailed
        if (!kA.contentEquals(sharedSecret)) throw Error.InvalidSignature

        // Get the sender's X25519 public key
        val senderX25519PublicKey = SodiumUtilities.toX25519(senderEdPublicKey) ?: throw Error.InvalidSignature

        val id = SessionId(IdPrefix.STANDARD, senderX25519PublicKey)
        return Pair(plaintext, id.hexString)
    }
}