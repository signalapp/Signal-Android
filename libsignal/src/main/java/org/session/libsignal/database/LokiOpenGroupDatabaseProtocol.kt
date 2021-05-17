package org.session.libsignal.database

interface LokiOpenGroupDatabaseProtocol {

    fun updateTitle(groupID: String, newValue: String)
    fun updateProfilePicture(groupID: String, newValue: ByteArray)
}
