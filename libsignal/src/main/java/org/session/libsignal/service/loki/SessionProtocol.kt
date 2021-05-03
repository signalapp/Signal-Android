package org.session.libsignal.service.loki.api.crypto

import org.session.libsignal.libsignal.ecc.ECKeyPair
import org.session.libsignal.service.loki.LokiAPIDatabaseProtocol

interface SessionProtocol {

    sealed class Exception(val description: String) : kotlin.Exception(description) {
        // Encryption
        object NoUserED25519KeyPair : Exception("Couldn't find user ED25519 key pair.")
        object SigningFailed : Exception("Couldn't sign message.")
        object EncryptionFailed : Exception("Couldn't encrypt message.")
        // Decryption
        object NoData : Exception("Received an empty envelope.")
        object InvalidGroupPublicKey : Exception("Invalid group public key.")
        object NoGroupKeyPair : Exception("Missing group key pair.")
        object DecryptionFailed : Exception("Couldn't decrypt message.")
        object InvalidSignature : Exception("Invalid message signature.")
    }
    /**
     * Decrypts `ciphertext` using the Session protocol and `x25519KeyPair`.
     *
     * @param ciphertext the data to decrypt.
     * @param x25519KeyPair the key pair to use for decryption. This could be the current user's key pair, or the key pair of a closed group.
     *
     * @return the padded plaintext.
     */
    fun decrypt(ciphertext: ByteArray, x25519KeyPair: ECKeyPair): Pair<ByteArray, String>
}

object SessionProtocolUtilities {

    fun decryptClosedGroupCiphertext(ciphertext: ByteArray, groupPublicKey: String, apiDB: LokiAPIDatabaseProtocol, sessionProtocolImpl: SessionProtocol): Pair<ByteArray, String> {
        val encryptionKeyPairs = apiDB.getClosedGroupEncryptionKeyPairs(groupPublicKey).toMutableList()
        if (encryptionKeyPairs.isEmpty()) { throw SessionProtocol.Exception.NoGroupKeyPair }
        // Loop through all known group key pairs in reverse order (i.e. try the latest key pair first (which'll more than
        // likely be the one we want) but try older ones in case that didn't work)
        var encryptionKeyPair = encryptionKeyPairs.removeAt(encryptionKeyPairs.lastIndex)
        fun decrypt(): Pair<ByteArray, String> {
            try {
                return sessionProtocolImpl.decrypt(ciphertext, encryptionKeyPair)
            } catch(exception: Exception) {
                if (encryptionKeyPairs.isNotEmpty()) {
                    encryptionKeyPair = encryptionKeyPairs.removeAt(encryptionKeyPairs.lastIndex)
                    return decrypt()
                } else {
                    throw exception
                }
            }
        }
        return decrypt()
    }
}