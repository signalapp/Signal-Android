package org.thoughtcrime.securesms.components.settings.conversation.sounds.custom

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.concurrent.SerialExecutor

class CustomNotificationsSettingsRepository(context: Context) {

  private val context = context.applicationContext
  private val executor = SerialExecutor(SignalExecutors.BOUNDED)

  fun ensureCustomChannelConsistency(recipientId: RecipientId, onComplete: () -> Unit) {
    executor.execute {
      if (NotificationChannels.supported()) {
        NotificationChannels.ensureCustomChannelConsistency(context)

        val recipient = Recipient.resolved(recipientId)
        val database = SignalDatabase.recipients
        if (recipient.notificationChannel != null) {
          database.setMessageRingtone(recipient.id, NotificationChannels.getMessageRingtone(context, recipient))
          database.setMessageVibrate(recipient.id, RecipientDatabase.VibrateState.fromBoolean(NotificationChannels.getMessageVibrate(context, recipient)))
        }
      }

      onComplete()
    }
  }

  fun setHasCustomNotifications(recipientId: RecipientId, hasCustomNotifications: Boolean) {
    executor.execute {
      if (hasCustomNotifications) {
        createCustomNotificationChannel(recipientId)
      } else {
        deleteCustomNotificationChannel(recipientId)
      }
    }
  }

  fun setMessageVibrate(recipientId: RecipientId, vibrateState: RecipientDatabase.VibrateState) {
    executor.execute {
      val recipient: Recipient = Recipient.resolved(recipientId)

      SignalDatabase.recipients.setMessageVibrate(recipient.id, vibrateState)
      NotificationChannels.updateMessageVibrate(context, recipient, vibrateState)
    }
  }

  fun setCallingVibrate(recipientId: RecipientId, vibrateState: RecipientDatabase.VibrateState) {
    executor.execute {
      SignalDatabase.recipients.setCallVibrate(recipientId, vibrateState)
    }
  }

  fun setMessageSound(recipientId: RecipientId, sound: Uri?) {
    executor.execute {
      val recipient: Recipient = Recipient.resolved(recipientId)
      val defaultValue = SignalStore.settings().messageNotificationSound
      val newValue: Uri? = if (defaultValue == sound) null else sound ?: Uri.EMPTY

      SignalDatabase.recipients.setMessageRingtone(recipient.id, newValue)
      NotificationChannels.updateMessageRingtone(context, recipient, newValue)
    }
  }

  fun setCallSound(recipientId: RecipientId, sound: Uri?) {
    executor.execute {
      val defaultValue = SignalStore.settings().callRingtone
      val newValue: Uri? = if (defaultValue == sound) null else sound ?: Uri.EMPTY

      SignalDatabase.recipients.setCallRingtone(recipientId, newValue)
    }
  }

  @WorkerThread
  private fun createCustomNotificationChannel(recipientId: RecipientId) {
    val recipient: Recipient = Recipient.resolved(recipientId)
    val channelId = NotificationChannels.createChannelFor(context, recipient)
    SignalDatabase.recipients.setNotificationChannel(recipient.id, channelId)
  }

  @WorkerThread
  private fun deleteCustomNotificationChannel(recipientId: RecipientId) {
    val recipient: Recipient = Recipient.resolved(recipientId)
    SignalDatabase.recipients.setNotificationChannel(recipient.id, null)
    NotificationChannels.deleteChannelFor(context, recipient)
  }
}
