package org.thoughtcrime.securesms.loki.redesign.utilities

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus

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
    val groupDatabase = DatabaseFactory.getGroupDatabase(context)
    val lokiUserDatabase = DatabaseFactory.getLokiUserDatabase(context)

    val ourDeviceLinks = lokiAPIDatabase.getDeviceLinks(userHexEncodedPublicKey)
    val ourDevices = ourDeviceLinks.flatMap {
      listOf( it.masterHexEncodedPublicKey.toLowerCase(), it.slaveHexEncodedPublicKey.toLowerCase() )
    }.toMutableSet()
    ourDevices.add(userHexEncodedPublicKey.toLowerCase())

    val cursor = threadDatabase.conversationList
    val result = mutableSetOf<Contact>()
    threadDatabase.readerFor(cursor).use { reader ->
      while (reader.next != null) {
        val thread = reader.current
        val recipient = thread.recipient
        val address = recipient.address.serialize()

        val isOurDevice = ourDevices.contains(address)
        val isFriend = lokiThreadDatabase.getFriendRequestStatus(thread.threadId) == LokiThreadFriendRequestStatus.FRIENDS
        var isSlave = false
        var displayName = ""

        if (!recipient.isGroupRecipient) {
          val deviceLinks = lokiAPIDatabase.getDeviceLinks(address)
          isSlave = deviceLinks.find { it.slaveHexEncodedPublicKey == address } != null
        }

        result.add(Contact(recipient, isFriend, isSlave, isOurDevice))
      }
    }

    return result
  }

}