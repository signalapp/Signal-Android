package org.thoughtcrime.securesms.loki

enum class LokiThreadFriendRequestStatus(val rawValue: Int) {
    /**
     * New conversation; no messages sent or received.
     */
    NONE(0),
    /**
     * Used to lock the input early while sending.
     */
    REQUEST_SENDING(1),
    /**
     * Friend request sent; awaiting response.
     */
    REQUEST_SENT(2),
    /**
     * Friend request received; awaiting user input.
     */
    REQUEST_RECEIVED(3),
    /**
     * The user is friends with the other user in this thread.
     */
    FRIENDS(4),
    /**
     * A friend request was sent, but it timed out (i.e the other user didn't accept within the allocated time).
     */
    REQUEST_EXPIRED(5)
}