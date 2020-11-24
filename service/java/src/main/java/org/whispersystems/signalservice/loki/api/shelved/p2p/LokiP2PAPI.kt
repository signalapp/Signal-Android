package org.whispersystems.signalservice.loki.api.shelved.p2p

import java.util.*
import kotlin.concurrent.timer

class LokiP2PAPI private constructor(private val userHexEncodedPublicKey: String, private val onPeerConnectionStatusChanged: (Boolean, String) -> Void, private val delegate: LokiP2PAPIDelegate) {
    internal val peerInfo = mutableMapOf<String, PeerInfo>()
    private val pingIntervals = mutableMapOf<String, Int>()
    private val timers = mutableMapOf<String, Timer>()

    // region Settings
    /**
     * The pinging interval for offline users.
     */
    private val offlinePingInterval = 2 * 60 * 1000
    // endregion

    // region Types
    internal data class PeerInfo(val contactHexEncodedPublicKey: String, val address: String, val port: Int, val isOnline: Boolean)
    // endregion

    // region Initialization
    companion object {
        private var isConfigured = false

        lateinit var shared: LokiP2PAPI

        /**
         * Must be called before `LokiAPI` is used.
         */
        fun configure(userHexEncodedPublicKey: String, onPeerConnectionStatusChanged: (Boolean, String) -> Void, delegate: LokiP2PAPIDelegate) {
            if (isConfigured) { return }
            shared = LokiP2PAPI(userHexEncodedPublicKey, onPeerConnectionStatusChanged, delegate)
            isConfigured = true
        }
    }
    // endregion

    // region Public API
    fun handlePeerInfoReceived(contactHexEncodedPublicKey: String, address: String, port: Int, isP2PMessage: Boolean) {
        // Avoid peers pinging eachother at the same time by staggering their timers
        val pingInterval = if (contactHexEncodedPublicKey < this.userHexEncodedPublicKey) 1 * 60 else 2 * 60
        pingIntervals[contactHexEncodedPublicKey] = pingInterval
        val oldPeerInfo = peerInfo[contactHexEncodedPublicKey]
        val newPeerInfo = PeerInfo(contactHexEncodedPublicKey, address, port, false)
        peerInfo[contactHexEncodedPublicKey] = newPeerInfo
        // Ping the peer back and mark them online based on the result of that call if either:
        // • We didn't know about the peer at all, i.e. no P2P connection was established yet during this session
        // • The message wasn't a P2P message, i.e. no P2P connection was established yet during this session or it was dropped for some reason
        // • The peer was marked offline before; test the new P2P connection
        // • The peer's address and/or port changed; test the new P2P connection
        if (oldPeerInfo == null || !isP2PMessage || !oldPeerInfo.isOnline || oldPeerInfo.address != address || oldPeerInfo.port != port) {
            delegate.ping(contactHexEncodedPublicKey)
        } else {
            mark(true, contactHexEncodedPublicKey)
        }
    }

    fun mark(isOnline: Boolean, contactHexEncodedPublicKey: String) {
        val oldTimer = timers[contactHexEncodedPublicKey]
        oldTimer?.cancel()
        val pingInterval = if (isOnline) { pingIntervals[contactHexEncodedPublicKey]!! } else { offlinePingInterval }
        val newTimer = timer(period = pingInterval.toLong()) { delegate.ping(contactHexEncodedPublicKey) }
        timers[contactHexEncodedPublicKey] = newTimer
        val updatedPeerInfo = peerInfo[contactHexEncodedPublicKey]!!.copy(isOnline = isOnline)
        peerInfo[contactHexEncodedPublicKey] = updatedPeerInfo
        onPeerConnectionStatusChanged(isOnline, contactHexEncodedPublicKey)
    }
    // endregion
}
