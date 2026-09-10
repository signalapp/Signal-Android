package org.thoughtcrime.securesms.components.settings.conversation.sounds.custom

import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.RecipientTable.VibrateState
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver
import org.thoughtcrime.securesms.recipients.RecipientId

class CustomNotificationsSettingsViewModel(
  private val recipientId: RecipientId
) : EventDrivenViewModel<CustomNotificationsEvents>(TAG), RecipientForeverObserver {

  companion object {
    private val TAG = Log.tag(CustomNotificationsSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(
    CustomNotificationsSettingsState(
      recipientId = recipientId,
      supportsNotificationChannels = NotificationChannels.supported(),
      canOpenChannelSettings = Build.VERSION.SDK_INT >= 30
    )
  )
  val state: StateFlow<CustomNotificationsSettingsState> = _state

  private val internalRingtonePickerRequests: Channel<RingtonePickerRequest> = Channel(Channel.BUFFERED)
  val ringtonePickerRequests: Flow<RingtonePickerRequest> = internalRingtonePickerRequests.receiveAsFlow()

  private val liveRecipient = Recipient.live(recipientId)

  init {
    liveRecipient.observeForever(this)
    onRecipientChanged(liveRecipient.get())
  }

  override fun onRecipientChanged(recipient: Recipient) {
    onEvent(CustomNotificationsEvents.RecipientChanged(recipient))
  }

  override fun onCleared() {
    liveRecipient.removeForeverObserver(this)
  }

  override suspend fun processEvent(event: CustomNotificationsEvents) {
    when (event) {
      CustomNotificationsEvents.Foregrounded -> applyForegroundedEvent()
      is CustomNotificationsEvents.RecipientChanged -> applyRecipientChangedEvent(event.recipient)
      is CustomNotificationsEvents.SetHasCustomNotifications -> CustomNotificationsSettingsRepository.setHasCustomNotifications(recipientId, event.enabled)
      is CustomNotificationsEvents.SetMessageSound -> CustomNotificationsSettingsRepository.setMessageSound(recipientId, event.uri)
      is CustomNotificationsEvents.SetMessageVibrate -> CustomNotificationsSettingsRepository.setMessageVibrate(recipientId, event.vibrateState)
      is CustomNotificationsEvents.SetCallSound -> CustomNotificationsSettingsRepository.setCallSound(recipientId, event.uri)
      is CustomNotificationsEvents.SetCallVibrate -> CustomNotificationsSettingsRepository.setCallingVibrate(recipientId, event.vibrateState)
      CustomNotificationsEvents.SelectMessageSound -> requestSound(RingtonePickerRequest.Target.MESSAGE, _state.value.messageSound)
      CustomNotificationsEvents.SelectCallSound -> requestSound(RingtonePickerRequest.Target.CALL, _state.value.callSound)
    }
  }

  /**
   * Re-syncs our recipient row with the system notification channel, which the user may have edited outside the app.
   * Controls stay disabled until it finishes.
   */
  private suspend fun applyForegroundedEvent() {
    _state.update { it.copy(isInitialLoadComplete = false) }

    CustomNotificationsSettingsRepository.ensureCustomChannelConsistency(recipientId)

    _state.update { it.copy(isInitialLoadComplete = true) }
  }

  private fun applyRecipientChangedEvent(recipient: Recipient) {
    _state.update {
      it.copy(
        notificationChannel = recipient.notificationChannel,
        messageSound = recipient.messageRingtone,
        messageVibrateState = recipient.messageVibrate,
        messageVibrateEnabled = when (recipient.messageVibrate) {
          VibrateState.DEFAULT -> SignalStore.settings.isMessageVibrateEnabled
          VibrateState.ENABLED -> true
          VibrateState.DISABLED -> false
        },
        showCallingOptions = recipient.isRegistered,
        callSound = recipient.callRingtone,
        callVibrateState = recipient.callVibrate
      )
    }
  }

  private fun requestSound(target: RingtonePickerRequest.Target, current: Uri?) {
    val existing: Uri? = when {
      current == null -> if (target == RingtonePickerRequest.Target.CALL) Settings.System.DEFAULT_RINGTONE_URI else Settings.System.DEFAULT_NOTIFICATION_URI
      current.toString().isEmpty() -> null
      else -> current
    }

    internalRingtonePickerRequests.trySend(RingtonePickerRequest(target, existing))
  }
}
