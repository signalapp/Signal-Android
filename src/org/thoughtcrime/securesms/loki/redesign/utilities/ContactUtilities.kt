package org.thoughtcrime.securesms.loki.redesign.utilities

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus

data class Contact(
  val recipient: Recipient,
  val threadId: Long,
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

    val ourDeviceLinks = lokiAPIDatabase.getDeviceLinks(userHexEncodedPublicKey)
    val ourDevices = ourDeviceLinks.flatMap {
      listOf( it.masterHexEncodedPublicKey.toLowerCase(), it.slaveHexEncodedPublicKey.toLowerCase() )
    }.toMutableSet()
    ourDevices.add(userHexEncodedPublicKey.toLowerCase())

    val cursor = threadDatabase.conversationList
    val reader = threadDatabase.readerFor(cursor)
    val result = mutableSetOf<Contact>()
    while (reader.next != null) {
      val thread = reader.current
      val recipient = thread.recipient
      val hexEncodedPublicKey = recipient.address.serialize()

      val isFriend = lokiThreadDatabase.getFriendRequestStatus(thread.threadId) == LokiThreadFriendRequestStatus.FRIENDS
      var isSlave = false
      if (!recipient.isGroupRecipient) {
        val deviceLinks = lokiAPIDatabase.getDeviceLinks(hexEncodedPublicKey)
        isSlave = deviceLinks.find { it.slaveHexEncodedPublicKey == hexEncodedPublicKey } != null
      }
      val isOurDevice = ourDevices.contains(hexEncodedPublicKey)

      result.add(Contact(recipient, thread.threadId, isFriend, isSlave, isOurDevice))
    }
    return result
  }

}