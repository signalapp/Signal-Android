package org.thoughtcrime.securesms.loki.redesign.messaging

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.protocol.mentions.MentionsManager

object LokiAPIUtilities {

    fun populateUserHexEncodedPublicKeyCacheIfNeeded(threadID: Long, context: Context) {
        if (MentionsManager.userHexEncodedPublicKeyCache[threadID] != null) { return }
        val result = mutableSetOf<String>()
        val messageDatabase = DatabaseFactory.getMmsSmsDatabase(context)
        val reader = messageDatabase.readerFor(messageDatabase.getConversation(threadID))
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
        result.add(TextSecurePreferences.getLocalNumber(context))
        MentionsManager.userHexEncodedPublicKeyCache[threadID] = result
    }
}