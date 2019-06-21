package org.thoughtcrime.securesms.loki

import org.thoughtcrime.securesms.sms.IncomingTextMessage

interface FriendRequestViewDelegate {
    /**
     * Implementations of this method should update the thread's friend request status
     * and send a friend request accepted message.
     */
    fun acceptFriendRequest(friendRequest: IncomingTextMessage)
    /**
     * Implementations of this method should update the thread's friend request status
     * and remove the pre keys associated with the contact.
     */
    fun rejectFriendRequest(friendRequest: IncomingTextMessage)
}