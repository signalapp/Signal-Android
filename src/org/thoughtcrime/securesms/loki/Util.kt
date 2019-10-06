package org.thoughtcrime.securesms.loki

import android.content.Context
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.loki.api.LokiGroupChatAPI
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus

fun isGroupChat(pubKey: String): Boolean {
  return (LokiGroupChatAPI.publicChatServer == pubKey)
}

fun getFriends(context: Context, devices: Set<String>): Set<String> {
  val lokiThreadDatabase = DatabaseFactory.getLokiThreadDatabase(context)

  return devices.mapNotNull { device ->
    val address = Address.fromSerialized(device)
    val recipient = Recipient.from(context, address, false)
    val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(recipient)
    if (threadID < 0) { return@mapNotNull null }

    if (lokiThreadDatabase.getFriendRequestStatus(threadID) == LokiThreadFriendRequestStatus.FRIENDS) device else null
  }.toSet()
}