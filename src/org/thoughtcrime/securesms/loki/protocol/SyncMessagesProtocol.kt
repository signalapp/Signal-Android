package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData
import org.thoughtcrime.securesms.contacts.ContactAccessor.NumberData
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.protocol.todo.LokiThreadFriendRequestStatus
import org.whispersystems.signalservice.loki.utilities.PublicKeyValidation
import java.util.*

object SyncMessagesProtocol {

    @JvmStatic
    fun shouldSyncReadReceipt(address: Address): Boolean {
        return !address.isGroup
    }

    @JvmStatic
    fun getContactsToSync(context: Context): List<ContactData> {
        val allAddresses = ArrayList(DatabaseFactory.getRecipientDatabase(context).allAddresses)
        val result = mutableSetOf<ContactData>()
        for (address in allAddresses) {
            if (!shouldSyncContact(context, address)) { continue }
            val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(Recipient.from(context, address, false))
            val displayName = DatabaseFactory.getLokiUserDatabase(context).getDisplayName(address.serialize())
            val contactData = ContactData(threadID, displayName)
            contactData.numbers.add(NumberData("TextSecure", address.serialize()))
            result.add(contactData)
        }
        return result.toList()
    }

    @JvmStatic
    fun shouldSyncContact(context: Context, address: Address): Boolean {
        if (!PublicKeyValidation.isValid(address.serialize())) { return false }
        if (address.serialize() == TextSecurePreferences.getMasterHexEncodedPublicKey(context)) { return false }
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(Recipient.from(context, address, false))
        val isFriend = DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadID) == LokiThreadFriendRequestStatus.FRIENDS
        return isFriend
    }
}