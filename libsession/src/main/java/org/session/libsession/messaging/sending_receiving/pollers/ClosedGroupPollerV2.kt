package org.session.libsession.messaging.sending_receiving.pollers

import nl.komponents.kovenant.Promise
import org.session.libsession.messaging.MessagingModuleConfiguration

class ClosedGroupPollerV2 {
    private var isPolling = mutableMapOf<String, Boolean>()

    private fun isPolling(groupPublicKey: String): Boolean {
        return isPolling[groupPublicKey] ?: false
    }

    companion object {
        private val minPollInterval = 4 * 1000
        private val maxPollInterval = 2 * 60 * 1000

        val shared = ClosedGroupPollerV2()
    }

    class InsufficientSnodesException() : Exception("No snodes left to poll.")
    class PollingCanceledException() : Exception("Polling canceled.")

    fun start() {
        val storage = MessagingModuleConfiguration.shared.storage
        val allGroupPublicKeys = storage.getAllClosedGroupPublicKeys()
        allGroupPublicKeys.forEach { startPolling(it) }
    }

    fun startPolling(groupPublicKey: String) {
        if (isPolling(groupPublicKey)) { return }
        setUpPolling(groupPublicKey)
        isPolling[groupPublicKey] = true
    }

    fun stop() {
        val storage = MessagingModuleConfiguration.shared.storage
        val allGroupPublicKeys = storage.getAllClosedGroupPublicKeys()
        allGroupPublicKeys.forEach { stopPolling(it) }
    }

    fun stopPolling(groupPublicKey: String) {
        // TODO: Invalidate future
        isPolling[groupPublicKey] = false
    }

    private fun setUpPolling(groupPublicKey: String) {
        poll(groupPublicKey).success {
            pollRecursively(groupPublicKey)
        }.fail {
            // The error is logged in poll(_:)
            pollRecursively(groupPublicKey)
        }
    }

    private fun pollRecursively(groupPublicKey: String) {
        if (!isPolling(groupPublicKey)) { return }
        // Get the received date of the last message in the thread. If we don't have any messages yet, pick some
        // reasonable fake time interval to use instead.

    }

    private fun poll(groupPublicKey: String): Promise<Unit, Exception> {
        return Promise.ofFail(InsufficientSnodesException())
    }

}
