package org.session.libsignal.libsignal.loki

import org.whispersystems.curve25519.Curve25519
import org.session.libsignal.libsignal.util.Hex
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded

/**
 * A session cipher that uses the current user's private key along with a contact's public key to encrypt data.
 */
class FallbackSessionCipher(private val userPrivateKey: ByteArray, private val hexEncodedContactPublicKey: String) {

    private val contactPublicKey by lazy {
        val hexEncodedContactPublicKey = hexEncodedContactPublicKey.removing05PrefixIfNeeded()
        Hex.fromStringCondensed(hexEncodedContactPublicKey)
    }

    private val symmetricKey: ByteArray?
        get() {
            try {
                val curve = Curve25519.getInstance(Curve25519.BEST)
                return curve.calculateAgreement(contactPublicKey, userPrivateKey)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

    companion object {
        @JvmStatic val sessionVersion = 3
    }

    fun encrypt(paddedMessageBody: ByteArray): ByteArray? {
        val symmetricKey = symmetricKey ?: return null
        try {
            return DiffieHellman.encrypt(paddedMessageBody, symmetricKey)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun decrypt(bytes: ByteArray): ByteArray? {
        val symmetricKey = symmetricKey ?: return null
        try {
            return DiffieHellman.decrypt(bytes, symmetricKey)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
