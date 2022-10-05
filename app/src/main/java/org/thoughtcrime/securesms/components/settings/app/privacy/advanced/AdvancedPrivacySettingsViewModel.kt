package org.thoughtcrime.securesms.components.settings.app.privacy.advanced

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.util.SingleLiveEvent
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.livedata.Store
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState

class AdvancedPrivacySettingsViewModel(
  private val sharedPreferences: SharedPreferences,
  private val repository: AdvancedPrivacySettingsRepository
) : ViewModel() {

  private val store = Store(getState())
  private val singleEvents = SingleLiveEvent<Event>()

  val state: LiveData<AdvancedPrivacySettingsState> = store.stateLiveData
  val events: LiveData<Event> = singleEvents
  val disposables: CompositeDisposable = CompositeDisposable()

  init {
    disposables.add(
      ApplicationDependencies.getSignalWebSocket().webSocketState
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { refresh() }
    )
  }

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
    ApplicationDependencies.getJobManager().startChain(RefreshAttributesJob()).then(RefreshOwnProfileJob()).enqueue()
    refresh()
  }

  fun setCensorshipCircumventionEnabled(enabled: Boolean) {
    SignalStore.settings().setCensorshipCircumventionEnabled(enabled)
    SignalStore.misc().isServiceReachableWithoutCircumvention = false
    ApplicationDependencies.resetAllNetworkConnections()
    refresh()
  }

  fun refresh() {
    store.update { getState().copy(showProgressSpinner = it.showProgressSpinner) }
  }

  override fun onCleared() {
    disposables.dispose()
  }

  private fun getState(): AdvancedPrivacySettingsState {
    val censorshipCircumventionState = getCensorshipCircumventionState()

    return AdvancedPrivacySettingsState(
      isPushEnabled = SignalStore.account().isRegistered,
      alwaysRelayCalls = TextSecurePreferences.isTurnOnly(ApplicationDependencies.getApplication()),
      censorshipCircumventionState = censorshipCircumventionState,
      censorshipCircumventionEnabled = getCensorshipCircumventionEnabled(censorshipCircumventionState),
      showSealedSenderStatusIcon = TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(
        ApplicationDependencies.getApplication()
      ),
      allowSealedSenderFromAnyone = TextSecurePreferences.isUniversalUnidentifiedAccess(
        ApplicationDependencies.getApplication()
      ),
      false
    )
  }

  private fun getCensorshipCircumventionState(): CensorshipCircumventionState {
    val countryCode: Int = PhoneNumberFormatter.getLocalCountryCode()
    val isCountryCodeCensoredByDefault: Boolean = ApplicationDependencies.getSignalServiceNetworkAccess().isCountryCodeCensoredByDefault(countryCode)
    val enabledState: SettingsValues.CensorshipCircumventionEnabled = SignalStore.settings().censorshipCircumventionEnabled
    val hasInternet: Boolean = NetworkConstraint.isMet(ApplicationDependencies.getApplication())
    val websocketConnected: Boolean = ApplicationDependencies.getSignalWebSocket().webSocketState.firstOrError().blockingGet() == WebSocketConnectionState.CONNECTED

    return when {
      SignalStore.internalValues().allowChangingCensorshipSetting() -> {
        CensorshipCircumventionState.AVAILABLE
      }
      isCountryCodeCensoredByDefault && enabledState == SettingsValues.CensorshipCircumventionEnabled.DISABLED -> {
        CensorshipCircumventionState.AVAILABLE_MANUALLY_DISABLED
      }
      isCountryCodeCensoredByDefault -> {
        CensorshipCircumventionState.AVAILABLE_AUTOMATICALLY_ENABLED
      }
      !hasInternet && enabledState != SettingsValues.CensorshipCircumventionEnabled.ENABLED -> {
        CensorshipCircumventionState.UNAVAILABLE_NO_INTERNET
      }
      websocketConnected && enabledState != SettingsValues.CensorshipCircumventionEnabled.ENABLED -> {
        CensorshipCircumventionState.UNAVAILABLE_CONNECTED
      }
      else -> {
        CensorshipCircumventionState.AVAILABLE
      }
    }
  }

  private fun getCensorshipCircumventionEnabled(state: CensorshipCircumventionState): Boolean {
    return when (state) {
      CensorshipCircumventionState.UNAVAILABLE_CONNECTED,
      CensorshipCircumventionState.UNAVAILABLE_NO_INTERNET,
      CensorshipCircumventionState.AVAILABLE_MANUALLY_DISABLED -> {
        false
      }
      CensorshipCircumventionState.AVAILABLE_AUTOMATICALLY_ENABLED -> {
        true
      }
      else -> {
        SignalStore.settings().censorshipCircumventionEnabled == SettingsValues.CensorshipCircumventionEnabled.ENABLED
      }
    }
  }

  enum class Event {
    DISABLE_PUSH_FAILED
  }

  class Factory(
    private val sharedPreferences: SharedPreferences,
    private val repository: AdvancedPrivacySettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
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
