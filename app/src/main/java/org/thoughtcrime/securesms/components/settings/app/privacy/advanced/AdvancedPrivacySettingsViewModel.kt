package org.thoughtcrime.securesms.components.settings.app.privacy.advanced

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.SingleLiveEvent
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.livedata.Store

class AdvancedPrivacySettingsViewModel(
  private val sharedPreferences: SharedPreferences,
  private val repository: AdvancedPrivacySettingsRepository
) : ViewModel() {

  private val store = Store(getState())
  private val singleEvents = SingleLiveEvent<Event>()

  val state: LiveData<AdvancedPrivacySettingsState> = store.stateLiveData
  val events: LiveData<Event> = singleEvents

  fun disablePushMessages() {
    store.update { getState().copy(showProgressSpinner = true) }

    repository.disablePushMessages {
      when (it) {
        AdvancedPrivacySettingsRepository.DisablePushMessagesResult.SUCCESS -> {
          SignalStore.account().setRegistered(false)
          SignalStore.registrationValues().clearRegistrationComplete()
          SignalStore.registrationValues().clearHasUploadedProfile()
        }
        AdvancedPrivacySettingsRepository.DisablePushMessagesResult.NETWORK_ERROR -> {
          singleEvents.postValue(Event.DISABLE_PUSH_FAILED)
        }
      }

      store.update { getState().copy(showProgressSpinner = false) }
    }
  }

  fun setAlwaysRelayCalls(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.ALWAYS_RELAY_CALLS_PREF, enabled).apply()
    refresh()
  }

  fun setShowStatusIconForSealedSender(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.SHOW_UNIDENTIFIED_DELIVERY_INDICATORS, enabled).apply()
    repository.syncShowSealedSenderIconState()
    refresh()
  }

  fun setAllowSealedSenderFromAnyone(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.UNIVERSAL_UNIDENTIFIED_ACCESS, enabled).apply()
    ApplicationDependencies.getJobManager().add(RefreshAttributesJob())
    refresh()
  }

  fun refresh() {
    store.update { getState().copy(showProgressSpinner = it.showProgressSpinner) }
  }

  private fun getState() = AdvancedPrivacySettingsState(
    isPushEnabled = SignalStore.account().isRegistered,
    alwaysRelayCalls = TextSecurePreferences.isTurnOnly(ApplicationDependencies.getApplication()),
    showSealedSenderStatusIcon = TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(
      ApplicationDependencies.getApplication()
    ),
    allowSealedSenderFromAnyone = TextSecurePreferences.isUniversalUnidentifiedAccess(
      ApplicationDependencies.getApplication()
    ),
    false
  )

  enum class Event {
    DISABLE_PUSH_FAILED
  }

  class Factory(
    private val sharedPreferences: SharedPreferences,
    private val repository: AdvancedPrivacySettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return requireNotNull(
        modelClass.cast(
          AdvancedPrivacySettingsViewModel(
            sharedPreferences,
            repository
          )
        )
      )
    }
  }
}
