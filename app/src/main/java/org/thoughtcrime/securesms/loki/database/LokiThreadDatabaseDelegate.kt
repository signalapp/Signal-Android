package org.thoughtcrime.securesms.loki.database

interface LokiThreadDatabaseDelegate {

    fun handleSessionRestoreDevicesChanged(threadID: Long)
}