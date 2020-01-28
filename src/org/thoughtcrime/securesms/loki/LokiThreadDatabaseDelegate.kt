package org.thoughtcrime.securesms.loki

interface LokiThreadDatabaseDelegate {

    fun handleThreadFriendRequestStatusChanged(threadID: Long)
    fun handleSessionRestoreDevicesChanged(threadID: Long)
}