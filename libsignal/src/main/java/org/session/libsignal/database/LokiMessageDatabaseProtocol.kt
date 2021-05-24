package org.session.libsignal.database

interface LokiMessageDatabaseProtocol {

    fun setServerID(messageID: Long, serverID: Long, isSms: Boolean)
}
