package org.thoughtcrime.securesms.loki

enum class LokiFriendRequestStatus(val rawValue: Int) {
    // New conversation; no messages sent or received.
    NONE(0),
    // This state is used to lock the input early while sending.
    REQUEST_SENDING(1),
    // Friend request sent; awaiting response.
    REQUEST_SENT(2),
    // Friend request received; awaiting user input.
    REQUEST_RECEIVED(3),
    // We are friends with the user in this thread.
    FRIENDS(4),
    // A friend request was sent, but it timed out (i.e other user didn't accept within the allocated time)
    REQUEST_EXPIRED(5)
}