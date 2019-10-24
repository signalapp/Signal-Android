package org.thoughtcrime.securesms.loki

import android.content.Context
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager

object LokiMessageSyncEvent {
  const val MESSAGE_SYNC_EVENT = "com.loki-network.messenger.MESSAGE_SYNC_EVENT"
  const val MESSAGE_ID = "message_id"
  const val TIMESTAMP = "timestamp"
  const val SYNC_MESSAGE = "sync_message"
  const val TTL = "ttl"

  fun broadcastSecurityUpdateEvent(context: Context, messageID: Long, timestamp: Long, message: ByteArray, ttl: Int) {
    val intent = Intent(MESSAGE_SYNC_EVENT)
    intent.putExtra(MESSAGE_ID, messageID)
    intent.putExtra(TIMESTAMP, timestamp)
    intent.putExtra(SYNC_MESSAGE, message)
    intent.putExtra(TTL, ttl)
    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
  }
}