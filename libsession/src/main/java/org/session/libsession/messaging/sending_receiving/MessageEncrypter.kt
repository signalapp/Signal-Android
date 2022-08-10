package org.session.libsession.messaging.sending_receiving

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.interfaces.Sign
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.sending_receiving.MessageSender.Error
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.removingIdPrefixIfNeeded

object MessageEncrypter {

    private val sodium by lazy { LazySodiumAndroid(SodiumAndroid()) }

    /**
     * Encrypts `plaintext` using the Session protocol for `hexEncodedX25519PublicKey`.
     *
     * @param plaintext the plaintext to encrypt. Must already be padded.
     * @param recipientHexEncodedX25519PublicKey the X25519 public key to encrypt for. Could be the Session ID of a user, or the public key of a closed group.
     *
     * @return the encrypted message.
     */
    internal fun encrypt(plaintext: ByteArray, recipientHexEncodedX25519PublicKey: String): ByteArray {
        val userED25519KeyPair = MessagingModuleConfiguration.shared.getUserED25519KeyPair() ?: throw Error.NoUserED25519KeyPair
        val recipientX25519PublicKey = Hex.fromStringCondensed(recipientHexEncodedX25519PublicKey.removingIdPrefixIfNeeded())

        val verificationData = plaintext + userED25519KeyPair.publicKey.asBytes + recipientX25519PublicKey
        val signature = ByteArray(Sign.BYTES)
        try {
            sodium.cryptoSignDetached(signature, verificationData, verificationData.size.toLong(), userED25519KeyPair.secretKey.asBytes)
        } catch (exception: Exception) {
            Log.d("Loki", "Couldn't sign message due to error: $exception.")
            throw Error.SigningFailed
        }
        val plaintextWithMetadata = plaintext + userED25519KeyPair.publicKey.asBytes + signature
        val ciphertext = ByteArray(plaintextWithMetadata.size + Box.SEALBYTES)
        try {
            sodium.cryptoBoxSeal(ciphertext, plaintextWithMetadata, plaintextWithMetadata.size.toLong(), recipientX25519PublicKey)
        } catch (exception: Exception) {
            Log.d("Loki", "Couldn't encrypt message due to error: $exception.")
            throw Error.EncryptionFailed
        }

        return ciphertext
    }

    internal fun encryptBlinded(
        plaintext: ByteArray,
        recipientBlindedId: String,
        serverPublicKey: String
    ): ByteArray {
        if (IdPrefix.fromValue(recipientBlindedId) != IdPrefix.BLINDED) throw Error.SigningFailed
        val userEdKeyPair =
            MessagingModuleConfiguration.shared.getUserED25519KeyPair() ?: throw Error.NoUserED25519KeyPair
        val blindedKeyPair = SodiumUtilities.blindedKeyPair(serverPublicKey, userEdKeyPair) ?: throw Error.SigningFailed
        val recipientBlindedPublicKey = Hex.fromStringCondensed(recipientBlindedId.removingIdPrefixIfNeeded())

        // Calculate the shared encryption key, sending from A to B
        val encryptionKey = SodiumUtilities.sharedBlindedEncryptionKey(
            userEdKeyPair.secretKey.asBytes,
            recipientBlindedPublicKey,
            blindedKeyPair.publicKey.asBytes,
            recipientBlindedPublicKey
        ) ?: throw Error.SigningFailed

        // Inner data: msg || A   (i.e. the sender's ed25519 master pubkey, *not* kA blinded pubkey)
        val message = plaintext + userEdKeyPair.publicKey.asBytes

        // Encrypt using xchacha20-poly1305
        val nonce = sodium.nonce(24)
        val ciphertext = SodiumUtilities.encrypt(message, encryptionKey, nonce) ?: throw Error.EncryptionFailed
        // data = b'\x00' + ciphertext + nonce
        return byteArrayOf(0.toByte()) + ciphertext + nonce
    }

}