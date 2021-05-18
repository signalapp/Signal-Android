package org.session.libsession.messaging.sending_receiving

import android.util.Log
import com.goterl.lazycode.lazysodium.LazySodiumAndroid
import com.goterl.lazycode.lazysodium.SodiumAndroid
import com.goterl.lazycode.lazysodium.interfaces.Box
import com.goterl.lazycode.lazysodium.interfaces.Sign
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.utilities.hexEncodedPublicKey
import org.session.libsignal.utilities.removing05PrefixIfNeeded
import org.session.libsignal.utilities.toHexString
import org.session.libsignal.utilities.Hex

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
        val recipientX25519PublicKey = Hex.fromStringCondensed(x25519KeyPair.hexEncodedPublicKey.removing05PrefixIfNeeded())
        val signatureSize = Sign.BYTES
        val ed25519PublicKeySize = Sign.PUBLICKEYBYTES

        // 1. ) Decrypt the message
        val plaintextWithMetadata = ByteArray(ciphertext.size - Box.SEALBYTES)
        try {
            sodium.cryptoBoxSealOpen(plaintextWithMetadata, ciphertext, ciphertext.size.toLong(), recipientX25519PublicKey, recipientX25519PrivateKey)
        } catch (exception: Exception) {
            Log.d("Loki", "Couldn't decrypt message due to error: $exception.")
            throw MessageReceiver.Error.DecryptionFailed
        }
        if (plaintextWithMetadata.size <= (signatureSize + ed25519PublicKeySize)) { throw MessageReceiver.Error.DecryptionFailed }
        // 2. ) Get the message parts
        val signature = plaintextWithMetadata.sliceArray(plaintextWithMetadata.size - signatureSize until plaintextWithMetadata.size)
        val senderED25519PublicKey = plaintextWithMetadata.sliceArray(plaintextWithMetadata.size - (signatureSize + ed25519PublicKeySize) until plaintextWithMetadata.size - signatureSize)
        val plaintext = plaintextWithMetadata.sliceArray(0 until plaintextWithMetadata.size - (signatureSize + ed25519PublicKeySize))
        // 3. ) Verify the signature
        val verificationData = (plaintext + senderED25519PublicKey + recipientX25519PublicKey)
        try {
            val isValid = sodium.cryptoSignVerifyDetached(signature, verificationData, verificationData.size, senderED25519PublicKey)
            if (!isValid) { throw MessageReceiver.Error.InvalidSignature }
        } catch (exception: Exception) {
            Log.d("Loki", "Couldn't verify message signature due to error: $exception.")
            throw MessageReceiver.Error.InvalidSignature
        }
        // 4. ) Get the sender's X25519 public key
        val senderX25519PublicKey = ByteArray(Sign.CURVE25519_PUBLICKEYBYTES)
        sodium.convertPublicKeyEd25519ToCurve25519(senderX25519PublicKey, senderED25519PublicKey)

        return Pair(plaintext, "05" + senderX25519PublicKey.toHexString())
    }
}