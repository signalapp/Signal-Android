package org.session.libsignal.service.loki.protocol.sessionmanagement

import org.session.libsignal.utilities.logging.Log
import org.session.libsignal.libsignal.loki.SessionResetProtocol
import org.session.libsignal.libsignal.loki.SessionResetStatus
import org.session.libsignal.libsignal.util.guava.Optional
import org.session.libsignal.service.api.SignalServiceMessageSender
import org.session.libsignal.service.api.push.SignalServiceAddress

public class SessionManagementProtocol(private val sessionResetImpl: SessionResetProtocol, private val delegate: SessionManagementProtocolDelegate) {

    // region Initialization
    companion object {

        public lateinit var shared: SessionManagementProtocol

        public fun configureIfNeeded(sessionResetImpl: SessionResetProtocol, delegate: SessionManagementProtocolDelegate) {
            if (::shared.isInitialized) { return; }
            shared = SessionManagementProtocol(sessionResetImpl, delegate)
        }
    }
    // endregion

    // region Sending
    /**
     * Called after an end session message is sent.
     */
    public fun setSessionResetStatusToInProgressIfNeeded(recipient: SignalServiceAddress, eventListener: Optional<SignalServiceMessageSender.EventListener>) {
        val publicKey = recipient.number
        val sessionResetStatus = sessionResetImpl.getSessionResetStatus(publicKey)
        if (sessionResetStatus == SessionResetStatus.REQUEST_RECEIVED) { return }
        Log.d("Loki", "Starting session reset")
        sessionResetImpl.setSessionResetStatus(publicKey, SessionResetStatus.IN_PROGRESS)
        if (!eventListener.isPresent) { return }
        eventListener.get().onSecurityEvent(recipient)
    }

    public fun repairSessionIfNeeded(recipient: SignalServiceAddress, isClosedGroup: Boolean) {
        val publicKey = recipient.number
        if (!isClosedGroup) { return }
        delegate.sendSessionRequestIfNeeded(publicKey)
    }

    public fun shouldIgnoreMissingPreKeyBundleException(isClosedGroup: Boolean): Boolean {
        // When a closed group is created, members try to establish sessions with eachother in the background through
        // session requests. Until ALL users those session requests were sent to have come online, stored the pre key
        // bundles contained in the session requests and replied with background messages to finalize the session
        // creation, a given user won't be able to successfully send a message to all members of a group. This check
        // is so that until we can do better on this front the user at least won't see this as an error in the UI.
        return isClosedGroup
    }
    // endregion
}
