@file:JvmName("NotificationUtilities")
package org.thoughtcrime.securesms.notifications

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient

fun getOpenGroupDisplayName(recipient: Recipient, threadRecipient: Recipient, context: Context): String {
    val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(threadRecipient)
    val publicChat = DatabaseFactory.getLokiThreadDatabase(context).getPublicChat(threadID)
    val hexEncodedPublicKey = recipient.address.toString()
    val displayName = if (publicChat != null) {
        DatabaseFactory.getLokiUserDatabase(context).getServerDisplayName(publicChat.id, hexEncodedPublicKey)
    } else {
        DatabaseFactory.getLokiUserDatabase(context).getDisplayName(hexEncodedPublicKey)
    }
    return displayName ?: hexEncodedPublicKey
}