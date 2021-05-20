package org.session.libsession.messaging.sending_receiving

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.interfaces.Sign
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.sending_receiving.MessageSender.Error
import org.session.libsession.utilities.KeyPairUtilities
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.removing05PrefixIfNeeded

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
    internal fun encrypt(plaintext: ByteArray, recipientHexEncodedX25519PublicKey: String): ByteArray{
        val context = MessagingModuleConfiguration.shared.context
        val userED25519KeyPair = KeyPairUtilities.getUserED25519KeyPair(context) ?: throw Error.NoUserED25519KeyPair
        val recipientX25519PublicKey = Hex.fromStringCondensed(recipientHexEncodedX25519PublicKey.removing05PrefixIfNeeded())

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

}