package org.session.libsignal.service.loki.database

interface LokiOpenGroupDatabaseProtocol {

    fun updateTitle(groupID: String, newValue: String)
    fun updateProfilePicture(groupID: String, newValue: ByteArray)
}
