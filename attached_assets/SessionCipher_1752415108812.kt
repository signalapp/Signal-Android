package org.thoughtcrime.securesms.crypto

import org.thoughtcrime.securesms.database.model.SessionRecord
import java.io.IOException

/**
 * A modified SessionCipher responsible for encrypting and decrypting messages for a
 * given session, now with FIPS handshake and routing capabilities.
 *
 * This class is the core of the dual-mode cryptographic system. Its responsibilities include:
 *
 * 1.  **Encryption Routing:** The `encrypt` method checks the session's current `SessionMode`.
 * If the mode is `Fips`, it routes the encryption call to the FIPS-validated module via
 * the native bridge. Otherwise, it uses the standard Signal primitives.
 *
 * 2.  **Decryption and Handshake Processing:** The `decrypt` method first decrypts a message
 * using the standard session keys. It then inspects the plaintext to check for a FIPS
 * handshake "magic number".
 * - If a FIPS invitation or response is found, it processes the handshake message
 * using the native bridge and updates the session state accordingly.
 * - If it's a regular message, it returns the plaintext to the application.
 *
 * @param sessionRecord The session record containing the current state, including keys
 * and the `SessionMode` flag.
 */
class SessionCipher(private val sessionRecord: SessionRecord) {

    /**
     * Encrypts a plaintext message using the appropriate cryptographic engine.
     *
     * @param plaintext The data to be encrypted.
     * @return A CiphertextMessage object containing the encrypted payload.
     * @throws IOException if the native encryption call fails.
     */
    fun encrypt(plaintext: ByteArray): CiphertextMessage {
        val state = sessionRecord.sessionState
        val sessionMode = state.getSessionMode()
        val chainKey = state.senderChainKey
        val messageKeys = chainKey.messageKeys

        Log.i(TAG, "Encrypting message for session ${sessionRecord.id} in mode: $sessionMode")

        // Use the FipsBridge to delegate the encryption call to the native layer.
        // The native CryptoRouter will handle selecting the correct algorithm (AES-GCM or XChaCha20)
        // based on the session mode we pass to it.
        val ciphertextBody = FipsBridge.createRouter().use { router ->
            router.encrypt(
                sessionId = sessionRecord.id,
                sessionMode = sessionMode,
                key = messageKeys.cipherKey,
                plaintext = plaintext
            )
        }

        // The rest of the message construction logic remains the same.
        val senderRatchetKey = state.senderRatchetKeyPair.publicKey
        val newChainKey = chainKey.nextChainKey
        state.senderChainKey = newChainKey

        return SignalMessage(
            senderRatchetKey = senderRatchetKey,
            counter = chainKey.index,
            ciphertext = ciphertextBody
        )
    }

    /**
     * Decrypts an incoming message and processes any potential FIPS handshake data.
     *
     * @param ciphertextMessage The incoming encrypted message.
     * @return A [DecryptionResult] containing either the plaintext or a handshake status.
     * @throws IOException if decryption fails.
     */
    fun decrypt(ciphertextMessage: SignalMessage): DecryptionResult {
        Log.i(TAG, "Decrypting message for session ${sessionRecord.id}")
        
        // Decryption always uses the standard session keys first, as the handshake
        // messages are transported over the standard channel.
        val plaintext = FipsBridge.createRouter().use { router ->
            // In a real implementation, we'd find the correct historical key first.
            val messageKeys = findMessageKeysForDecryption(sessionRecord.sessionState, ciphertextMessage)
                ?: throw IOException("Could not find message key for decryption.")

            router.decrypt(
                sessionId = sessionRecord.id,
                sessionMode = SessionMode.STANDARD, // Always decrypt transport channel as standard
                key = messageKeys.cipherKey,
                ciphertext = ciphertextMessage.ciphertext
            )
        }

        // After decryption, inspect the plaintext for a FIPS handshake message.
        FipsBridge.createRouter().use { router ->
            val handshakeResponse = router.processFipsHandshakeMessage(plaintext)

            if (handshakeResponse != null) {
                // The message was a FIPS Invitation. We need to send a FIPS Response.
                Log.i(TAG, "Processed FIPS Invitation, sending FIPS Response.")
                sessionRecord.sessionState.setSessionMode(SessionMode.FIPS) // Upgrade session
                sessionStore.storeSession(sessionRecord.recipientId, sessionRecord)
                return DecryptionResult.HandshakeResponse(handshakeResponse)
            } else if (router.isFipsFinalized(plaintext)) {
                // The message was a FIPS Response. The session is now fully FIPS.
                Log.i(TAG, "Processed FIPS Response. Session is now fully FIPS compliant.")
                sessionRecord.sessionState.setSessionMode(SessionMode.FIPS)
                sessionStore.storeSession(sessionRecord.recipientId, sessionRecord)
                return DecryptionResult.HandshakeComplete
            }
        }

        // If it was not a handshake message, it's regular user content.
        return DecryptionResult.Plaintext(plaintext)
    }
    
    // --- Helper and Dummy Classes ---
    
    sealed class DecryptionResult {
        data class Plaintext(val data: ByteArray) : DecryptionResult()
        data class HandshakeResponse(val payload: ByteArray) : DecryptionResult()
        object HandshakeComplete : DecryptionResult()
    }

    private fun findMessageKeysForDecryption(state: SessionState, message: SignalMessage): MessageKeys? {
        // Placeholder for complex logic to find the correct historical message key.
        return state.getReceiverChain(message.senderRatchetKey)?.chainKey?.messageKeys
    }

    companion object {
        private const val TAG = "FipsSessionCipher"
        private fun Log.i(tag: String, message: String) { println("[$tag] $message") }
    }
    
    // Dummy classes to make the example compile.
    class SessionStore {
        fun storeSession(id: RecipientId, record: SessionRecord) {}
    }
    val sessionStore = SessionStore() // Dummy instance

    class SignalMessage(val senderRatchetKey: ECKeyPair, val counter: Int, val ciphertext: ByteArray) : CiphertextMessage()
    open class CiphertextMessage
    class SessionRecord {
        val id = 123L
        val recipientId = RecipientId.from(1)
        val sessionState = SessionState()
    }
    class SessionState {
        var senderChainKey = ChainKey()
        val senderRatchetKeyPair = ECKeyPair()
        private var mode = SessionMode.STANDARD
        fun getSessionMode(): SessionMode = mode
        fun setSessionMode(newMode: SessionMode) { mode = newMode }
        fun getReceiverChain(key: ECKeyPair): ReceiverChain? = ReceiverChain()
    }
    class ChainKey {
        val messageKeys = MessageKeys()
        val nextChainKey = ChainKey()
        val index = 1
    }
    class MessageKeys { val cipherKey = ByteArray(32) }
    class ECKeyPair
    class ReceiverChain { val chainKey = ChainKey() }
    class RecipientId { companion object { fun from(long: Long) = RecipientId() } }
    enum class SessionMode { STANDARD, FIPS_NEGOTIATING, FIPS }
}

// Dummy FipsRouter extension methods for this example
fun FipsRouter.decrypt(sessionId: Long, sessionMode: SessionCipher.SessionMode, key: ByteArray, ciphertext: ByteArray): ByteArray {
    // STUB: In a real implementation, this would call a native decrypt method.
    return ciphertext.reversedArray()
}
fun FipsRouter.processFipsHandshakeMessage(plaintext: ByteArray): ByteArray? {
    // STUB: In a real implementation, this would call a native method.
    if (plaintext.contentEquals("FIPS_INVITATION".toByteArray())) return "FIPS_RESPONSE".toByteArray()
    return null
}
fun FipsRouter.isFipsFinalized(plaintext: ByteArray): Boolean {
    // STUB: In a real implementation, this would call a native method.
    return plaintext.contentEquals("FIPS_RESPONSE".toByteArray())
}
