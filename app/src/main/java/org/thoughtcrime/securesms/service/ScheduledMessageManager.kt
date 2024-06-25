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
import org.thoughtcrime.securesms.jobs.IndividualSendJob
import org.thoughtcrime.securesms.jobs.PushGroupSendJob
import org.thoughtcrime.securesms.recipients.RecipientId
import kotlin.time.Duration.Companion.seconds

/**
 * Manages waking up and sending scheduled messages at the correct time
 */
class ScheduledMessageManager(
  val application: Application
) : TimedEventManager<ScheduledMessageManager.Event>(application, "ScheduledMessagesManager") {

  companion object {
    private val TAG = Log.tag(ScheduledMessageManager::class.java)
  }

  private val messagesTable = SignalDatabase.messages

  init {
    scheduleIfNecessary()
  }

  @Suppress("UsePropertyAccessSyntax")
  @WorkerThread
  override fun getNextClosestEvent(): Event? {
    val oldestMessage: MmsMessageRecord? = messagesTable.getOldestScheduledSendTimestamp() as? MmsMessageRecord

    if (oldestMessage == null) {
      cancelAlarm(application, ScheduledMessagesAlarm::class.java)
      return null
    }

    val delay = (oldestMessage.scheduledDate - System.currentTimeMillis()).coerceAtLeast(0)
    Log.i(TAG, "The next scheduled message needs to be sent in $delay ms.")

    return Event(delay, oldestMessage.toRecipient.id, oldestMessage.threadId)
  }

  @WorkerThread
  override fun executeEvent(event: Event) {
    val scheduledMessagesToSend = messagesTable.getScheduledMessagesBefore(System.currentTimeMillis())
    for (record in scheduledMessagesToSend) {
      val expiresIn = SignalDatabase.recipients.getExpiresInSeconds(record.toRecipient.id)
      if (messagesTable.clearScheduledStatus(record.threadId, record.id, expiresIn.seconds.inWholeMilliseconds)) {
        if (record.toRecipient.isPushGroup) {
          PushGroupSendJob.enqueue(application, AppDependencies.jobManager, record.id, record.toRecipient.id, emptySet(), true)
        } else {
          IndividualSendJob.enqueue(application, AppDependencies.jobManager, record.id, record.toRecipient, true)
        }
      } else {
        Log.i(TAG, "messageId=${record.id} was not a scheduled message, ignoring")
      }
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
      ScheduledMessagesAlarm::class.java,
      PendingIntent.getActivity(application, 0, conversationIntent, PendingIntentFlags.mutable())
    )
  }

  data class Event(val delay: Long, val recipientId: RecipientId, val threadId: Long)

  class ScheduledMessagesAlarm : BroadcastReceiver() {

    companion object {
      private val TAG = Log.tag(ScheduledMessagesAlarm::class.java)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
      Log.d(TAG, "onReceive()")
      AppDependencies.scheduledMessageManager.scheduleIfNecessary()
    }
  }
}
