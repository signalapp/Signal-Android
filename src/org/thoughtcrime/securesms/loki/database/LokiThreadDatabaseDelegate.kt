package org.thoughtcrime.securesms.loki.database

interface LokiThreadDatabaseDelegate {

    fun handleThreadFriendRequestStatusChanged(threadID: Long)
    fun handleSessionRestoreDevicesChanged(threadID: Long)
}