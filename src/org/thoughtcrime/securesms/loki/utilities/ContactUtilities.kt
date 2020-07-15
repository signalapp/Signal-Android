package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.protocol.multidevice.MultiDeviceProtocol

data class Contact(
    val recipient: Recipient,
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
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val lokiAPIDatabase = DatabaseFactory.getLokiAPIDatabase(context)
        val userDevices = MultiDeviceProtocol.shared.getAllLinkedDevices(userPublicKey)
        val cursor = threadDatabase.conversationList
        val result = mutableSetOf<Contact>()
        threadDatabase.readerFor(cursor).use { reader ->
        while (reader.next != null) {
            val thread = reader.current
            val recipient = thread.recipient
            val publicKey = recipient.address.serialize()
            val isUserDevice = userDevices.contains(publicKey)
            var isSlave = false
            if (!recipient.isGroupRecipient) {
                val deviceLinks = lokiAPIDatabase.getDeviceLinks(publicKey)
                isSlave = deviceLinks.find { it.slavePublicKey == publicKey } != null
            }
            result.add(Contact(recipient, isSlave, isUserDevice))
        }
    }
    return result
  }
}