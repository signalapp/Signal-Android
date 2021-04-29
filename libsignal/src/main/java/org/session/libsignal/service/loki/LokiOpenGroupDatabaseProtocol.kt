package org.session.libsignal.service.loki

interface LokiOpenGroupDatabaseProtocol {

    fun updateTitle(groupID: String, newValue: String)
    fun updateProfilePicture(groupID: String, newValue: ByteArray)
}
