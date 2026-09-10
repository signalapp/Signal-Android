package org.thoughtcrime.securesms.components.settings.conversation.sounds.custom

import android.net.Uri
import androidx.annotation.WorkerThread
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * All of the storage and notification channel access behind [CustomNotificationsSettingsViewModel].
 *
 * Channels and the recipient rows that point at them have to stay in sync, so every write here runs one at a time.
 */
object CustomNotificationsSettingsRepository {

  private val mutex = Mutex()

  suspend fun ensureCustomChannelConsistency(recipientId: RecipientId) = serialized {
    if (NotificationChannels.supported()) {
      NotificationChannels.getInstance().ensureCustomChannelConsistency()

      val recipient = Recipient.resolved(recipientId)
      val database = SignalDatabase.recipients
      if (recipient.notificationChannel != null) {
        val ringtoneUri: Uri? = NotificationChannels.getInstance().getMessageRingtone(recipient)
        database.setMessageRingtone(recipient.id, if (ringtoneUri == Uri.EMPTY) null else ringtoneUri)
        database.setMessageVibrate(recipient.id, RecipientTable.VibrateState.fromBoolean(NotificationChannels.getInstance().getMessageVibrate(recipient)))
      }
    }
  }

  suspend fun setHasCustomNotifications(recipientId: RecipientId, hasCustomNotifications: Boolean) = serialized {
    if (hasCustomNotifications) {
      createCustomNotificationChannel(recipientId)
    } else {
      deleteCustomNotificationChannel(recipientId)
    }
  }

  suspend fun setMessageVibrate(recipientId: RecipientId, vibrateState: RecipientTable.VibrateState) = serialized {
    val recipient: Recipient = Recipient.resolved(recipientId)

    SignalDatabase.recipients.setMessageVibrate(recipient.id, vibrateState)
    NotificationChannels.getInstance().updateMessageVibrate(recipient, vibrateState)
  }

  suspend fun setCallingVibrate(recipientId: RecipientId, vibrateState: RecipientTable.VibrateState) = serialized {
    SignalDatabase.recipients.setCallVibrate(recipientId, vibrateState)
  }

  suspend fun setMessageSound(recipientId: RecipientId, sound: Uri?) = serialized {
    val recipient: Recipient = Recipient.resolved(recipientId)
    val defaultValue = SignalStore.settings.messageNotificationSound
    val newValue: Uri? = if (defaultValue == sound) null else sound ?: Uri.EMPTY

    SignalDatabase.recipients.setMessageRingtone(recipient.id, newValue)
    NotificationChannels.getInstance().updateMessageRingtone(recipient, newValue)
  }

  suspend fun setCallSound(recipientId: RecipientId, sound: Uri?) = serialized {
    val defaultValue = SignalStore.settings.callRingtone
    val newValue: Uri? = if (defaultValue == sound) null else sound ?: Uri.EMPTY

    SignalDatabase.recipients.setCallRingtone(recipientId, newValue)
  }

  private suspend fun <T> serialized(block: () -> T): T {
    return mutex.withLock {
      withContext(SignalDispatchers.Default) {
        block()
      }
    }
  }

  @WorkerThread
  private fun createCustomNotificationChannel(recipientId: RecipientId) {
    val recipient: Recipient = Recipient.resolved(recipientId)
    val channelId = NotificationChannels.getInstance().createChannelFor(recipient)
    SignalDatabase.recipients.setNotificationChannel(recipient.id, channelId)
  }

  @WorkerThread
  private fun deleteCustomNotificationChannel(recipientId: RecipientId) {
    val recipient: Recipient = Recipient.resolved(recipientId)
    SignalDatabase.recipients.setNotificationChannel(recipient.id, null)
    NotificationChannels.getInstance().deleteChannelFor(recipient)
  }
}
