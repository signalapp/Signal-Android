package org.thoughtcrime.securesms.components.settings.app.privacy.expire

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.livedata.ProcessState
import org.thoughtcrime.securesms.util.livedata.Store

class ExpireTimerSettingsViewModel(val config: Config, private val repository: ExpireTimerSettingsRepository) : ViewModel() {

  private val store = Store<ExpireTimerSettingsState>(ExpireTimerSettingsState(isGroupCreate = config.forResultMode))
  private val recipientId: RecipientId? = config.recipientId

  val state: LiveData<ExpireTimerSettingsState> = store.stateLiveData

  init {
    if (recipientId != null) {
      store.update(Recipient.live(recipientId).liveData) { r, s -> s.copy(initialTimer = r.expiresInSeconds, isForRecipient = true) }
    } else {
      store.update { it.copy(initialTimer = config.initialValue ?: SignalStore.settings.universalExpireTimer) }
    }
  }

  fun select(time: Int) {
    store.update { it.copy(userSetTimer = time) }
  }

  fun save() {
    val userSetTimer: Int = store.state.currentTimer

    if (userSetTimer == store.state.initialTimer) {
      store.update { it.copy(saveState = ProcessState.Success(userSetTimer)) }
      return
    }

    store.update { it.copy(saveState = ProcessState.Working()) }
    if (recipientId != null) {
      repository.setExpiration(recipientId, userSetTimer) { result ->
        store.update { it.copy(saveState = ProcessState.fromResult(result)) }
      }
    } else if (config.forResultMode) {
      store.update { it.copy(saveState = ProcessState.Success(userSetTimer)) }
    } else {
      repository.setUniversalExpireTimerSeconds(userSetTimer) {
        store.update { it.copy(saveState = ProcessState.Success(userSetTimer)) }
      }
    }
  }

  fun resetError() {
    store.update { it.copy(saveState = ProcessState.Idle()) }
  }

  class Factory(context: Context, private val config: Config) : ViewModelProvider.Factory {
    val repository = ExpireTimerSettingsRepository(context.applicationContext)

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(ExpireTimerSettingsViewModel(config, repository)))
    }
  }

  data class Config(
    val recipientId: RecipientId? = null,
    val forResultMode: Boolean = false,
    val initialValue: Int? = null
  )
}
