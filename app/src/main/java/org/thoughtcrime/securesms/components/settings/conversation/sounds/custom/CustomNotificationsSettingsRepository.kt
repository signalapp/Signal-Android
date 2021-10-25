package org.thoughtcrime.securesms.components.settings.conversation.sounds.custom

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.concurrent.SerialExecutor

class CustomNotificationsSettingsRepository(context: Context) {

  private val context = context.applicationContext
  private val executor = SerialExecutor(SignalExecutors.BOUNDED)

  fun initialize(recipientId: RecipientId, onInitializationComplete: () -> Unit) {
    executor.execute {
      val recipient = Recipient.resolved(recipientId)
      val database = DatabaseFactory.getRecipientDatabase(context)

      if (NotificationChannels.supported() && recipient.notificationChannel != null) {
        database.setMessageRingtone(recipient.id, NotificationChannels.getMessageRingtone(context, recipient))
        database.setMessageVibrate(recipient.id, RecipientDatabase.VibrateState.fromBoolean(NotificationChannels.getMessageVibrate(context, recipient)))

        NotificationChannels.ensureCustomChannelConsistency(context)
      }

      onInitializationComplete()
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

      DatabaseFactory.getRecipientDatabase(context).setMessageVibrate(recipient.id, vibrateState)
      NotificationChannels.updateMessageVibrate(context, recipient, vibrateState)
    }
  }

  fun setCallingVibrate(recipientId: RecipientId, vibrateState: RecipientDatabase.VibrateState) {
    executor.execute {
      DatabaseFactory.getRecipientDatabase(context).setCallVibrate(recipientId, vibrateState)
    }
  }

  fun setMessageSound(recipientId: RecipientId, sound: Uri?) {
    executor.execute {
      val recipient: Recipient = Recipient.resolved(recipientId)
      val defaultValue = SignalStore.settings().messageNotificationSound
      val newValue: Uri? = if (defaultValue == sound) null else sound ?: Uri.EMPTY

      DatabaseFactory.getRecipientDatabase(context).setMessageRingtone(recipient.id, newValue)
      NotificationChannels.updateMessageRingtone(context, recipient, newValue)
    }
  }

  fun setCallSound(recipientId: RecipientId, sound: Uri?) {
    executor.execute {
      val defaultValue = SignalStore.settings().callRingtone
      val newValue: Uri? = if (defaultValue == sound) null else sound ?: Uri.EMPTY

      DatabaseFactory.getRecipientDatabase(context).setCallRingtone(recipientId, newValue)
    }
  }

  @WorkerThread
  private fun createCustomNotificationChannel(recipientId: RecipientId) {
    val recipient: Recipient = Recipient.resolved(recipientId)
    val channelId = NotificationChannels.createChannelFor(context, recipient)
    DatabaseFactory.getRecipientDatabase(context).setNotificationChannel(recipient.id, channelId)
  }

  @WorkerThread
  private fun deleteCustomNotificationChannel(recipientId: RecipientId) {
    val recipient: Recipient = Recipient.resolved(recipientId)
    DatabaseFactory.getRecipientDatabase(context).setNotificationChannel(recipient.id, null)
    NotificationChannels.deleteChannelFor(context, recipient)
  }
}
