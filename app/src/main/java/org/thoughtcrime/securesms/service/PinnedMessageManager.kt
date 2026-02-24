package org.thoughtcrime.securesms.service

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.GroupUtil
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage

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
      val dataMessageBuilder = SignalServiceDataMessage.newBuilder()
        .withTimestamp(System.currentTimeMillis())
        .withUnpinnedMessage(
          SignalServiceDataMessage.UnpinnedMessage(
            targetAuthor = record.fromRecipient.requireServiceId(),
            targetSentTimestamp = record.dateSent
          )
        )

      val conversationRecipient = SignalDatabase.threads.getRecipientForThreadId(record.threadId) ?: continue
      if (conversationRecipient.isGroup) {
        GroupUtil.setDataMessageGroupContext(application, dataMessageBuilder, conversationRecipient.requireGroupId().requirePush())
      }
      AppDependencies.signalServiceMessageSender.sendSyncMessage(dataMessageBuilder.build())
    }
  }

  @WorkerThread
  override fun getDelayForEvent(event: Event): Long = event.delay

  @WorkerThread
  override fun scheduleAlarm(application: Application, event: Event, delay: Long) {
    setAlarm(
      application,
      System.currentTimeMillis() + delay,
      PinnedMessagesAlarm::class.java
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
