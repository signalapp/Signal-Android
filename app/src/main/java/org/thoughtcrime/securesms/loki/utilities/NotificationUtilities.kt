@file:JvmName("NotificationUtilities")
package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.session.libsession.utilities.recipients.Recipient

fun getOpenGroupDisplayName(recipient: Recipient, threadRecipient: Recipient, context: Context): String {
    val threadID = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(threadRecipient)
    val publicChat = DatabaseFactory.getLokiThreadDatabase(context).getPublicChat(threadID)
    val publicKey = recipient.address.toString()
    val displayName = if (publicChat != null) {
        DatabaseFactory.getLokiUserDatabase(context).getServerDisplayName(publicChat.id, publicKey)
    } else {
        DatabaseFactory.getLokiUserDatabase(context).getDisplayName(publicKey)
    }
    return displayName ?: publicKey
}