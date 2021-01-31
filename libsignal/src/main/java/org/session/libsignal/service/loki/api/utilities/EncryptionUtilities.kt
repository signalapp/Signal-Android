package org.session.libsignal.service.loki.api.utilities

import org.whispersystems.curve25519.Curve25519
import org.session.libsignal.libsignal.util.ByteUtil
import org.session.libsignal.utilities.Hex
import org.session.libsignal.service.internal.util.Util
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal data class EncryptionResult(
    internal val ciphertext: ByteArray,
    internal val symmetricKey: ByteArray,
    internal val ephemeralPublicKey: ByteArray
)

internal object EncryptionUtilities {
    internal val gcmTagSize = 128
    internal val ivSize = 12

    /**
     * Sync. Don't call from the main thread.
     */
    internal fun encryptUsingAESGCM(plaintext: ByteArray, symmetricKey: ByteArray): ByteArray {
        val iv = Util.getSecretBytes(ivSize)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(symmetricKey, "AES"), GCMParameterSpec(gcmTagSize, iv))
        return ByteUtil.combine(iv, cipher.doFinal(plaintext))
    }

    /**
     * Sync. Don't call from the main thread.
     */
    internal fun encryptForX25519PublicKey(plaintext: ByteArray, hexEncodedX25519PublicKey: String): EncryptionResult {
        val x25519PublicKey = Hex.fromStringCondensed(hexEncodedX25519PublicKey)
        val ephemeralKeyPair = Curve25519.getInstance(Curve25519.BEST).generateKeyPair()
        val ephemeralSharedSecret = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(x25519PublicKey, ephemeralKeyPair.privateKey)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("LOKI".toByteArray(), "HmacSHA256"))
        val symmetricKey = mac.doFinal(ephemeralSharedSecret)
        val ciphertext = encryptUsingAESGCM(plaintext, symmetricKey)
        return EncryptionResult(ciphertext, symmetricKey, ephemeralKeyPair.publicKey)
    }
}
