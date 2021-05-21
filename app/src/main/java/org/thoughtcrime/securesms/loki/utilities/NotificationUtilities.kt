@file:JvmName("NotificationUtilities")
package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.session.libsession.utilities.recipients.Recipient

fun getOpenGroupDisplayName(recipient: Recipient, threadRecipient: Recipient, context: Context): String {
    val publicKey = recipient.address.toString()
    val displayName = DatabaseFactory.getLokiUserDatabase(context).getDisplayName(publicKey)
    // FIXME: Add short ID here?
    return displayName ?: publicKey
}