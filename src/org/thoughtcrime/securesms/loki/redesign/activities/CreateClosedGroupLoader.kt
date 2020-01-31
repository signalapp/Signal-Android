package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.util.AsyncLoader
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus

class CreateClosedGroupLoader(context: Context) : AsyncLoader<List<String>>(context) {

    override fun loadInBackground(): List<String> {
        val threadDatabase = DatabaseFactory.getThreadDatabase(context)
        val lokiThreadDatabase = DatabaseFactory.getLokiThreadDatabase(context)
        val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context)
        val deviceLinks = DatabaseFactory.getLokiAPIDatabase(context).getPairingAuthorisations(userHexEncodedPublicKey)
        val userLinkedDeviceHexEncodedPublicKeys = deviceLinks.flatMap {
            listOf( it.primaryDevicePublicKey.toLowerCase(), it.secondaryDevicePublicKey.toLowerCase() )
        }.toMutableSet()
        userLinkedDeviceHexEncodedPublicKeys.add(userHexEncodedPublicKey.toLowerCase())
        val cursor = threadDatabase.conversationList
        val reader = threadDatabase.readerFor(cursor)
        val result = mutableListOf<String>()
        while (reader.next != null) {
            val thread = reader.current
            if (thread.recipient.isGroupRecipient) { continue }
            if (lokiThreadDatabase.getFriendRequestStatus(thread.threadId) != LokiThreadFriendRequestStatus.FRIENDS) { continue }
            val hexEncodedPublicKey = thread.recipient.address.toString().toLowerCase()
            if (userLinkedDeviceHexEncodedPublicKeys.contains(hexEncodedPublicKey)) { continue }
            result.add(hexEncodedPublicKey)
        }
        return result
    }
}