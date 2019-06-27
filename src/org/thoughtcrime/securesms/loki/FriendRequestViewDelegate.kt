package org.thoughtcrime.securesms.loki

import org.thoughtcrime.securesms.database.model.MessageRecord

interface FriendRequestViewDelegate {
    /**
     * Implementations of this method should update the thread's friend request status
     * and send a friend request accepted message.
     */
    fun acceptFriendRequest(friendRequest: MessageRecord)
    /**
     * Implementations of this method should update the thread's friend request status
     * and remove the pre keys associated with the contact.
     */
    fun rejectFriendRequest(friendRequest: MessageRecord)
}