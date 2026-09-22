package org.thoughtcrime.securesms.components.settings.app.notifications

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.RecipientTable.NotificationSetting
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper

class MutedNotificationsViewModel(private val recipientId: RecipientId? = null) : EventDrivenViewModel<MutedNotificationsEvent>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(MutedNotificationsViewModel::class)
  }

  private val _state = MutableStateFlow(MutedNotificationsState())
  val state = _state.asStateFlow()

  init {
    val observedRecipientId = recipientId ?: Recipient.self().id

    viewModelScope.launch(SignalDispatchers.Default) {
      Recipient.observable(observedRecipientId).asFlow().collectLatest { recipient ->
        if (recipientId != null) {
          _state.update {
            it.copy(
              isGlobal = false,
              allowCalls = recipient.callNotificationSetting == NotificationSetting.ALWAYS_NOTIFY,
              showMentions = recipient.isPushV2Group,
              allowMentions = recipient.mentionSetting == NotificationSetting.ALWAYS_NOTIFY,
              showReplies = recipient.isPushV2Group,
              allowReplies = recipient.replyNotificationSetting == NotificationSetting.ALWAYS_NOTIFY
            )
          }
        } else {
          _state.update {
            it.copy(
              isGlobal = true,
              allowCalls = SignalStore.settings.allowCallsWhileMuted,
              showMentions = true,
              allowMentions = SignalStore.settings.allowMentionsWhileMuted,
              showReplies = true,
              allowReplies = SignalStore.settings.allowRepliesWhileMuted
            )
          }
        }
      }
    }
  }

  override suspend fun processEvent(event: MutedNotificationsEvent) {
    applyEvent(_state.value, event) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: MutedNotificationsState, event: MutedNotificationsEvent, stateEmitter: (MutedNotificationsState) -> Unit) {
    when (event) {
      is MutedNotificationsEvent.CallsToggled -> {
        if (recipientId != null) {
          withContext(SignalDispatchers.Default) {
            SignalDatabase.recipients.setCallNotificationSetting(recipientId, if (event.allowCalls) NotificationSetting.ALWAYS_NOTIFY else NotificationSetting.DO_NOT_NOTIFY)
          }
        } else {
          SignalStore.settings.allowCallsWhileMuted = event.allowCalls
          markSelfNeedsSync()
        }
        stateEmitter(state.copy(allowCalls = event.allowCalls))
      }
      is MutedNotificationsEvent.MentionsToggled -> {
        if (recipientId != null) {
          withContext(SignalDispatchers.Default) {
            SignalDatabase.recipients.setMentionSetting(recipientId, if (event.allowMentions) NotificationSetting.ALWAYS_NOTIFY else NotificationSetting.DO_NOT_NOTIFY)
          }
        } else {
          SignalStore.settings.allowMentionsWhileMuted = event.allowMentions
          markSelfNeedsSync()
        }
        stateEmitter(state.copy(allowMentions = event.allowMentions))
      }
      is MutedNotificationsEvent.RepliesToggled -> {
        if (recipientId != null) {
          withContext(SignalDispatchers.Default) {
            SignalDatabase.recipients.setReplyNotificationSetting(recipientId, if (event.allowReplies) NotificationSetting.ALWAYS_NOTIFY else NotificationSetting.DO_NOT_NOTIFY)
          }
        } else {
          SignalStore.settings.allowRepliesWhileMuted = event.allowReplies
          markSelfNeedsSync()
        }
        stateEmitter(state.copy(allowReplies = event.allowReplies))
      }
    }
  }

  private suspend fun markSelfNeedsSync() {
    withContext(SignalDispatchers.Default) {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  class Factory(private val recipientId: RecipientId? = null) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(MutedNotificationsViewModel(recipientId))!!
    }
  }
}

data class MutedNotificationsState(
  val isGlobal: Boolean = true,
  val allowCalls: Boolean = SignalStore.settings.allowCallsWhileMuted,
  val showMentions: Boolean = true,
  val allowMentions: Boolean = SignalStore.settings.allowMentionsWhileMuted,
  val showReplies: Boolean = true,
  val allowReplies: Boolean = SignalStore.settings.allowRepliesWhileMuted
)
