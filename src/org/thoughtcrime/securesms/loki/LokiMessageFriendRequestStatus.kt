package org.thoughtcrime.securesms.loki

enum class LokiMessageFriendRequestStatus(val rawValue: Int) {
    NONE(0),
    REQUEST_SENDING_OR_FAILED(1),
    /**
     * Either sent or received.
     */
    REQUEST_PENDING(2),
    REQUEST_ACCEPTED(3),
    REQUEST_REJECTED(4),
    REQUEST_EXPIRED(5)
}