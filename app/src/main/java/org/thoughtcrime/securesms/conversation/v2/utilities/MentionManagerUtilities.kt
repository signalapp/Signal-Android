package org.thoughtcrime.securesms.conversation.v2.utilities

import android.content.Context
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.messaging.mentions.MentionsManager
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.MessageRecord

object MentionManagerUtilities {

    fun populateUserPublicKeyCacheIfNeeded(threadID: Long, context: Context) {
        // exit early if we need to
        if (MentionsManager.userPublicKeyCache[threadID] != null) return

        val result = mutableSetOf<String>()
        val recipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadID) ?: return
        if (recipient.address.isClosedGroup) {
            val members = DatabaseFactory.getGroupDatabase(context).getGroupMembers(recipient.address.toGroupString(), false).map { it.address.serialize() }
            result.addAll(members)
        } else {
            val messageDatabase = DatabaseFactory.getMmsSmsDatabase(context)
            val reader = messageDatabase.readerFor(messageDatabase.getConversation(threadID, 0, 200))
            var record: MessageRecord? = reader.next
            while (record != null) {
                result.add(record.individualRecipient.address.serialize())
                try {
                    record = reader.next
                } catch (exception: Exception) {
                    record = null
                }
            }
            reader.close()
            result.add(TextSecurePreferences.getLocalNumber(context)!!)
        }
        MentionsManager.userPublicKeyCache[threadID] = result
    }
}