package org.thoughtcrime.securesms.crypto

import android.content.Context
import org.thoughtcrime.securesms.database.model.SessionRecord
import org.thoughtcrime.securesms.database.model.SessionType
import org.thoughtcrime.securesms.recipients.RecipientId
import java.io.IOException

/**
 * A modified SessionBuilder responsible for constructing a new session.
 *
 * This class has been refactored to support the "Opportunistic FIPS Handshake" model.
 * It introduces a new `process` method that accepts a boolean flag to determine whether
 * a FIPS-compliant session should be attempted.
 *
 * If `attemptFipsSession` is true, this builder will:
 * 1. Establish a normal, standard-mode session with the recipient.
 * 2. Immediately use the [FipsBridge] to create a special "FIPS Invitation" message.
 * 3. Encrypt the invitation using the newly created standard session.
 * 4. Return this encrypted invitation as the first outgoing message to the recipient.
 * 5. Mark the new session record as being in a "FIPS Negotiating" state.
 *
 * This class is the first piece of application logic to actively use the native
 * FIPS bridge to implement the client-only protocol.
 *
 * @param context The Android application context.
 * @param sessionStore The store for session records.
 * @param preKeyStore The store for pre-keys.
 * @param signedPreKeyStore The store for signed pre-keys.
 * @param identityKeyStore The store for identity keys.
 */
class SessionBuilder(
    private val context: Context,
    private val sessionStore: SessionStore,
    private val preKeyStore: PreKeyStore,
    private val signedPreKeyStore: SignedPreKeyStore,
    private val identityKeyStore: IdentityKeyStore
) {

    /**
     * Processes a PreKeyBundle to establish a new session with a recipient.
     *
     * @param recipientId The ID of the recipient.
     * @param preKeyBundle The pre-key bundle fetched from the server.
     * @param attemptFipsSession If true, will attempt to establish a FIPS-compliant session
     * using the opportunistic handshake protocol. If false, establishes a standard session.
     * @return A PreKeySignalMessage to be sent, which will be the FIPS invitation if
     * `attemptFipsSession` is true, or null if no message needs to be sent immediately.
     */
    @Throws(IOException::class, UntrustedIdentityException::class)
    fun process(
        recipientId: RecipientId,
        preKeyBundle: PreKeyBundle,
        attemptFipsSession: Boolean
    ): PreKeySignalMessage? {
        // 1. Establish a standard session regardless of the desired final mode.
        // This provides the secure channel needed to transport the FIPS handshake.
        val standardSessionRecord = establishStandardSession(recipientId, preKeyBundle)
        
        if (!attemptFipsSession) {
            // If we are not attempting a FIPS session, we are done.
            // The standard session is established and ready for use.
            return null
        }

        // 2. The user wants a FIPS session. We must now create and send the invitation.
        Log.i(TAG, "Attempting opportunistic FIPS handshake for session with ${recipientId.serialize()}")

        // 3. Use the FipsBridge to create the special invitation payload from the native layer.
        val fipsInvitationPayload = FipsBridge.createRouter().use { router ->
            router.createFipsInvitation()
        }

        // 4. Encrypt the invitation payload using the standard session we just created.
        val sessionCipher = SessionCipher(standardSessionRecord)
        val invitationMessage = sessionCipher.encrypt(fipsInvitationPayload) as PreKeySignalMessage

        // 5. Mark the session record as being in the "FIPS Negotiating" state.
        // This flag will be checked when processing incoming messages to handle the FIPS response.
        standardSessionRecord.sessionState.setSessionMode(SessionMode.FIPS_NEGOTIATING)
        sessionStore.storeSession(recipientId, standardSessionRecord)

        Log.i(TAG, "FIPS invitation created and encrypted. Ready to send.")
        return invitationMessage
    }

    /**
     * Establishes a standard session with a recipient. This logic is based on the
     * original Signal protocol's session establishment process.
     */
    @Throws(UntrustedIdentityException::class)
    private fun establishStandardSession(recipientId: RecipientId, preKeyBundle: PreKeyBundle): SessionRecord {
        val identityKey = preKeyBundle.identityKey
        // Identity validation logic...
        // ...

        val sessionRecord = SessionRecord(SessionType.V2)
        // The core X3DH logic would be performed here, using the preKeyBundle.
        // This involves multiple ECDH operations with the standard Curve25519 keys.
        // For brevity, we are representing this complex process as a single call.
        sessionRecord.sessionState.performX3DH(preKeyBundle, identityKeyStore.identityKeyPair)
        
        sessionStore.storeSession(recipientId, sessionRecord)
        identityKeyStore.saveIdentity(recipientId, identityKey)
        
        return sessionRecord
    }

    // Dummy classes and properties to make the example compile.
    // In the real Signal app, these are complex, fully-featured classes.
    companion object {
        private const val TAG = "FipsSessionBuilder"
        private fun Log.i(tag: String, message: String) { println("[$tag] $message") }
    }

    enum class SessionMode {
        STANDARD,
        FIPS_NEGOTIATING,
        FIPS
    }

    class SessionStore {
        fun storeSession(id: RecipientId, record: SessionRecord) {}
    }
    class PreKeyStore
    class SignedPreKeyStore
    class IdentityKeyStore {
        val identityKeyPair = ECKeyPair()
        fun saveIdentity(id: RecipientId, key: IdentityKey) {}
    }
    class PreKeyBundle {
        val identityKey = IdentityKey()
    }
    class PreKeySignalMessage
    class UntrustedIdentityException : Exception()
    class SessionCipher(record: SessionRecord) {
        fun encrypt(payload: ByteArray): PreKeySignalMessage = PreKeySignalMessage()
    }
    class SessionRecord(type: SessionType) {
        val sessionState = SessionState()
        fun getSessionState(): SessionState = sessionState
    }
    class SessionState {
        fun performX3DH(bundle: PreKeyBundle, keyPair: ECKeyPair) {}
        fun setSessionMode(mode: SessionMode) {}
    }
    class ECKeyPair
    class IdentityKey
}
