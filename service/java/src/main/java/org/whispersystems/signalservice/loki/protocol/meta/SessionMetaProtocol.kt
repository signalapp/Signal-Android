package org.whispersystems.signalservice.loki.protocol.meta

import org.whispersystems.signalservice.loki.database.LokiAPIDatabaseProtocol
import org.whispersystems.signalservice.loki.protocol.shelved.multidevice.MultiDeviceProtocol

public class SessionMetaProtocol(private val apiDatabase: LokiAPIDatabaseProtocol, private val userPublicKey: String) {

    // region Initialization
    companion object {

        public lateinit var shared: SessionMetaProtocol

        public fun configureIfNeeded(apiDatabase: LokiAPIDatabaseProtocol, userPublicKey: String) {
            if (::shared.isInitialized) { return; }
            shared = SessionMetaProtocol(apiDatabase, userPublicKey)
        }
    }
    // endregion

    // region Utilities
    public fun isNoteToSelf(publicKey: String): Boolean {
        return userPublicKey == publicKey // return MultiDeviceProtocol.shared.getAllLinkedDevices(userPublicKey).contains(publicKey)
    }
    // endregion
}
