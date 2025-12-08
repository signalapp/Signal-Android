package org.thoughtcrime.securesms.service

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Manages waking up and unpinning pinned messages at the correct time
 */
class PinnedMessageManager(
  val application: Application
) : TimedEventManager<PinnedMessageManager.Event>(application, "PinnedMessagesManager") {

  companion object {
    private val TAG = Log.tag(PinnedMessageManager::class.java)
  }

  private val messagesTable = SignalDatabase.messages

  init {
    scheduleIfNecessary()
  }

  @WorkerThread
  override fun getNextClosestEvent(): Event? {
    val oldestMessage: MmsMessageRecord? = messagesTable.getOldestExpiringPinnedMessageTimestamp() as? MmsMessageRecord

    if (oldestMessage == null) {
      cancelAlarm(application, PinnedMessagesAlarm::class.java)
      return null
    }

    val delay = (oldestMessage.pinnedUntil - System.currentTimeMillis()).coerceAtLeast(0)
    Log.i(TAG, "The next pinned message needs to be unpinned in $delay ms.")

    return Event(delay, oldestMessage.toRecipient.id, oldestMessage.threadId)
  }

  @WorkerThread
  override fun executeEvent(event: Event) {
    val pinnedMessagesToUnpin = messagesTable.getPinnedMessagesBefore(System.currentTimeMillis())
    for (record in pinnedMessagesToUnpin) {
      messagesTable.unpinMessage(messageId = record.id, threadId = record.threadId)
      // TODO(michelle): Send sync message to linked device to unpin message (done to ensure consistency)
    }
  }

  @WorkerThread
  override fun getDelayForEvent(event: Event): Long = event.delay

  @WorkerThread
  override fun scheduleAlarm(application: Application, event: Event, delay: Long) {
    val conversationIntent = ConversationIntents.createBuilderSync(application, event.recipientId, event.threadId).build()

    trySetExactAlarm(
      application,
      System.currentTimeMillis() + delay,
      PinnedMessagesAlarm::class.java,
      PendingIntent.getActivity(application, 0, conversationIntent, PendingIntentFlags.mutable())
    )
  }

  data class Event(val delay: Long, val recipientId: RecipientId, val threadId: Long)

  class PinnedMessagesAlarm : BroadcastReceiver() {

    companion object {
      private val TAG = Log.tag(PinnedMessagesAlarm::class.java)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
      Log.d(TAG, "onReceive()")
      AppDependencies.pinnedMessageManager.scheduleIfNecessary()
    }
  }
}
