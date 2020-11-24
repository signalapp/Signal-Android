package org.whispersystems.signalservice.loki.database

import org.whispersystems.signalservice.loki.api.opengroups.PublicChat

interface LokiThreadDatabaseProtocol {

    fun getThreadID(publicKey: String): Long
    fun getPublicChat(threadID: Long): PublicChat?
    fun setPublicChat(publicChat: PublicChat, threadID: Long)
    fun removePublicChat(threadID: Long)
}
