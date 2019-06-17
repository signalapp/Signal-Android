package org.thoughtcrime.securesms.loki;

public class LokiFriendRequestStatus {
    // New conversation; no messages sent or received.
    public static final int NONE = 0;
    // This state is used to lock the input early while sending.
    public static final int REQUEST_SENDING = 1;
    // Friend request sent; awaiting response.
    public static final int REQUEST_SENT = 2;
    // Friend request received; awaiting user input.
    public static final int REQUEST_RECEIVED = 3;
    // We are friends with the user in this thread.
    public static final int FRIENDS = 4;
    // A friend request was sent, but it timed out (i.e other user didn't accept within the allocated time)
    public static final int REQUEST_EXPIRED = 5;
}

