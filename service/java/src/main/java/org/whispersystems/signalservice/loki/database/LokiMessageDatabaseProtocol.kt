package org.whispersystems.signalservice.loki.database

interface LokiMessageDatabaseProtocol {

    fun getQuoteServerID(quoteID: Long, quoteePublicKey: String): Long?
    fun setServerID(messageID: Long, serverID: Long)
}
