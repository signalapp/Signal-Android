package org.session.libsignal.service.loki.protocol.meta

public object TTLUtilities {

    /**
     * If a message type specifies an invalid TTL, this will be used.
     */
    public val fallbackMessageTTL = 2 * 24 * 60 * 60 * 1000

    public enum class MessageType {
        // Unimportant control messages
        Address, Call, TypingIndicator, Verified,
        // Somewhat important control messages
        DeviceLink,
        // Important control messages
        ClosedGroupUpdate, Ephemeral, SessionRequest, Receipt, Sync, DeviceUnlinkingRequest,
        // Visible messages
        Regular
    }

    @JvmStatic
    public fun getTTL(messageType: MessageType): Int {
        val minuteInMs = 60 * 1000
        val hourInMs = 60 * minuteInMs
        val dayInMs = 24 * hourInMs
        return when (messageType) {
            // Unimportant control messages
            MessageType.Address, MessageType.Call, MessageType.TypingIndicator, MessageType.Verified -> 1 * minuteInMs
            // Somewhat important control messages
            MessageType.DeviceLink -> 1 * hourInMs
            // Important control messages
            MessageType.ClosedGroupUpdate, MessageType.Ephemeral, MessageType.SessionRequest, MessageType.Receipt,
            MessageType.Sync, MessageType.DeviceUnlinkingRequest -> 2 * dayInMs - 1 * hourInMs
            // Visible messages
            MessageType.Regular -> 2 * dayInMs
        }
    }
}
