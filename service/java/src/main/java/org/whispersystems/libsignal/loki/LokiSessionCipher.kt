package org.whispersystems.libsignal.loki

import org.whispersystems.libsignal.DecryptionCallback
import org.whispersystems.libsignal.SessionCipher
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import org.whispersystems.libsignal.state.SessionState
import org.whispersystems.libsignal.state.SignalProtocolStore

/**
 * A wrapper class for `SessionCipher`.
 * This applies session reset logic on decryption.
 */
class LokiSessionCipher(private val protocolStore: SignalProtocolStore, private var sessionResetProtocol: SessionResetProtocol, val address: SignalProtocolAddress) : SessionCipher(protocolStore, address) {

    override fun decrypt(ciphertext: PreKeySignalMessage?, callback: DecryptionCallback?): ByteArray {
        // Record the current session state as it may change during decryption
        val activeSession = getCurrentSessionState()
        if (activeSession == null && ciphertext != null) {
            sessionResetProtocol.validatePreKeySignalMessage(address.name, ciphertext)
        }
        val plainText = super.decrypt(ciphertext, callback)
        handleSessionResetRequestIfNeeded(activeSession)
        return plainText
    }

    override fun decrypt(ciphertext: SignalMessage?, callback: DecryptionCallback?): ByteArray {
        // Record the current session state as it may change during decryption
        val activeSession = getCurrentSessionState()
        val plainText = super.decrypt(ciphertext, callback)
        handleSessionResetRequestIfNeeded(activeSession)
        return plainText
    }

    private fun getCurrentSessionState(): SessionState? {
        val sessionRecord = protocolStore.loadSession(address)
        return sessionRecord.sessionState
    }

    private fun handleSessionResetRequestIfNeeded(oldSession: SessionState?) {
        if (oldSession == null) { return }
        val publicKey = address.name
        val currentSessionResetStatus = sessionResetProtocol.getSessionResetStatus(publicKey)
        if (currentSessionResetStatus == SessionResetStatus.NONE) return
        val currentSession = getCurrentSessionState()
        if (currentSession == null || currentSession.aliceBaseKey?.contentEquals(oldSession.aliceBaseKey) != true) {
            if (currentSessionResetStatus == SessionResetStatus.REQUEST_RECEIVED) {
                // The other user used an old session to contact us; wait for them to switch to a new one.
                restoreSession(oldSession)
            } else {
                // Our session reset was successful; we initiated one and got a new session back from the other user.
                deleteAllSessionsExcept(currentSession)
                sessionResetProtocol.setSessionResetStatus(publicKey, SessionResetStatus.NONE)
                sessionResetProtocol.onNewSessionAdopted(publicKey, currentSessionResetStatus)
            }
        } else if (currentSessionResetStatus == SessionResetStatus.REQUEST_RECEIVED) {
            // Our session reset was successful; we received a message with the same session from the other user.
            deleteAllSessionsExcept(oldSession)
            sessionResetProtocol.setSessionResetStatus(publicKey, SessionResetStatus.NONE)
            sessionResetProtocol.onNewSessionAdopted(publicKey, currentSessionResetStatus)
        }
    }

    private fun restoreSession(state: SessionState) {
        val session = protocolStore.loadSession(address)
        session.previousSessionStates.removeAll { it.aliceBaseKey?.contentEquals(state.aliceBaseKey) ?: false }
        session.promoteState(state)
        protocolStore.storeSession(address, session)
    }

    private fun deleteAllSessionsExcept(state: SessionState?) {
        val sessionRecord = protocolStore.loadSession(address)
        sessionRecord.removePreviousSessionStates()
        sessionRecord.setState(state ?: SessionState())
        protocolStore.storeSession(address, sessionRecord)
    }
}
