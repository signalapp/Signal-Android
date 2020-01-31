package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.util.AsyncLoader
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus

class CreateClosedGroupLoader(context: Context) : AsyncLoader<List<String>>(context) {

    override fun loadInBackground(): List<String> {
        val threadDatabase = DatabaseFactory.getThreadDatabase(context)
        val lokiThreadDatabase = DatabaseFactory.getLokiThreadDatabase(context)
        val cursor = threadDatabase.conversationList
        val reader = threadDatabase.readerFor(cursor)
        val result = mutableListOf<String>()
        while (reader.next != null) {
            val thread = reader.current
            if (thread.recipient.isGroupRecipient) { continue }
            if (lokiThreadDatabase.getFriendRequestStatus(thread.threadId) != LokiThreadFriendRequestStatus.FRIENDS) { continue }
            result.add(thread.recipient.address.toString())
        }
        return result
    }
}