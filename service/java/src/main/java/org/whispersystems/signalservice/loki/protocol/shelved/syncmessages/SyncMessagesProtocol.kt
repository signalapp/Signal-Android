package org.whispersystems.signalservice.loki.protocol.shelved.syncmessages

import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceGroup
import org.whispersystems.signalservice.loki.database.LokiAPIDatabaseProtocol

public class SyncMessagesProtocol(private val apiDatabase: LokiAPIDatabaseProtocol, private val userPublicKey: String) {

    // region Initialization
    companion object {

        public lateinit var shared: SyncMessagesProtocol

        public fun configureIfNeeded(apiDatabase: LokiAPIDatabaseProtocol, userPublicKey: String) {
            if (Companion::shared.isInitialized) { return; }
            shared = SyncMessagesProtocol(apiDatabase, userPublicKey)
        }
    }
    // endregion

    // region Sending
    /**
     * Note: This is called only if based on Signal's logic we'd want to send a sync message.
     */
    public fun shouldSyncMessage(message: SignalServiceDataMessage): Boolean {
        return false
        /*
        if (message.deviceLink.isPresent) { return false }
        val isOpenGroupMessage = message.group.isPresent && message.group.get().groupType == SignalServiceGroup.GroupType.PUBLIC_CHAT
        if (isOpenGroupMessage) { return false }
        val usesMultiDevice = apiDatabase.getDeviceLinks(userPublicKey).isNotEmpty()
        return usesMultiDevice
         */
    }
    // endregion
}
