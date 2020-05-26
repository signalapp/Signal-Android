package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.protocol.multidevice.MultiDeviceProtocol
import org.whispersystems.signalservice.loki.protocol.todo.LokiThreadFriendRequestStatus

data class Contact(
    val recipient: Recipient,
    val isFriend: Boolean,
    val isSlave: Boolean,
    val isOurDevice: Boolean
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as Contact
        return recipient == other.recipient
    }

    override fun hashCode(): Int {
        return recipient.hashCode()
    }
}

object ContactUtilities {

    @JvmStatic
    fun getAllContacts(context: Context): Set<Contact> {
        val threadDatabase = DatabaseFactory.getThreadDatabase(context)
        val lokiThreadDatabase = DatabaseFactory.getLokiThreadDatabase(context)
        val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context)
        val lokiAPIDatabase = DatabaseFactory.getLokiAPIDatabase(context)
        val userDevices = MultiDeviceProtocol.shared.getAllLinkedDevices(userHexEncodedPublicKey)
        val cursor = threadDatabase.conversationList
        val result = mutableSetOf<Contact>()
        threadDatabase.readerFor(cursor).use { reader ->
        while (reader.next != null) {
            val thread = reader.current
            val recipient = thread.recipient
            val publicKey = recipient.address.serialize()
            val isUserDevice = userDevices.contains(publicKey)
            val isFriend = lokiThreadDatabase.getFriendRequestStatus(thread.threadId) == LokiThreadFriendRequestStatus.FRIENDS
            var isSlave = false
            if (!recipient.isGroupRecipient) {
                val deviceLinks = lokiAPIDatabase.getDeviceLinks(publicKey)
                isSlave = deviceLinks.find { it.slaveHexEncodedPublicKey == publicKey } != null
            }
            result.add(Contact(recipient, isFriend, isSlave, isUserDevice))
        }
    }
    return result
  }
}