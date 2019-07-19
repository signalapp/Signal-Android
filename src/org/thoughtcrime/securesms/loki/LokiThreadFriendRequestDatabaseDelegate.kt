package org.thoughtcrime.securesms.loki

interface LokiThreadFriendRequestDatabaseDelegate {

    fun handleThreadFriendRequestStatusChanged(threadID: Long)
}