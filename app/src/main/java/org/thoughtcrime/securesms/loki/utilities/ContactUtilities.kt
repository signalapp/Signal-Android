package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.session.libsession.utilities.recipients.Recipient

object ContactUtilities {

    @JvmStatic
    fun getAllContacts(context: Context): Set<Recipient> {
        val threadDatabase = DatabaseFactory.getThreadDatabase(context)
        val cursor = threadDatabase.conversationList
        val result = mutableSetOf<Recipient>()
        threadDatabase.readerFor(cursor).use { reader ->
            while (reader.next != null) {
                val thread = reader.current
                val recipient = thread.recipient
                result.add(recipient)
            }
        }
        return result
    }
}