package org.session.libsignal.service.loki.api.crypto

import org.session.libsignal.service.api.messages.SignalServiceEnvelope

interface SessionProtocol {

    sealed class Exception(val description: String) : kotlin.Exception(description) {
        // Encryption
        object NoUserED25519KeyPair : Exception("Couldn't find user ED25519 key pair.")
        object SigningFailed : Exception("Couldn't sign message.")
        object EncryptionFailed : Exception("Couldn't encrypt message.")
        // Decryption
        object NoData : Exception("Received an empty envelope.")
        object InvalidGroupPublicKey : Exception("Invalid group public key.")
        object NoGroupPrivateKey : Exception("Missing group private key.")
        object DecryptionFailed : Exception("Couldn't decrypt message.")
        object InvalidSignature : Exception("Invalid message signature.")
    }

    /**
     * Encrypts `plaintext` using the Session protocol for `hexEncodedX25519PublicKey`.
     *
     * @param plaintext the plaintext to encrypt. Must already be padded.
     * @param recipientHexEncodedX25519PublicKey the X25519 public key to encrypt for. Could be the Session ID of a user, or the public key of a closed group.
     *
     * @return the encrypted message.
     */
    fun encrypt(plaintext: ByteArray, recipientHexEncodedX25519PublicKey: String): ByteArray

    /**
     * Decrypts `envelope.content` using the Session protocol. If the envelope type is `UNIDENTIFIED_SENDER` the message is assumed to be a one-to-one
     * message. If the envelope type is `CLOSED_GROUP_CIPHERTEXT` the message is assumed to be a closed group message. In the latter case `envelope.source`
     * must be set to the closed group's public key.
     *
     * @param envelope the envelope for which to decrypt the content.
     *
     * @return the padded plaintext.
     */
    fun decrypt(envelope: SignalServiceEnvelope): Pair<ByteArray, String>
}