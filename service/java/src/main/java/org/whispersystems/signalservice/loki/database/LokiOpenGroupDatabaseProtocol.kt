package org.whispersystems.signalservice.loki.database

interface LokiOpenGroupDatabaseProtocol {

    fun updateTitle(groupID: String, newValue: String)
    fun updateProfilePicture(groupID: String, newValue: ByteArray)
}
