package org.thoughtcrime.securesms.components.settings.app.chats.sms

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.livedata.Store

class SmsSettingsViewModel : ViewModel() {

  private val repository = SmsSettingsRepository()

  private val disposables = CompositeDisposable()
  private val store = Store(
    SmsSettingsState(
      useAsDefaultSmsApp = Util.isDefaultSmsProvider(ApplicationDependencies.getApplication()),
      smsDeliveryReportsEnabled = SignalStore.settings().isSmsDeliveryReportsEnabled,
      wifiCallingCompatibilityEnabled = SignalStore.settings().isWifiCallingCompatibilityModeEnabled
    )
  )

  val state: LiveData<SmsSettingsState> = store.stateLiveData

  init {
    disposables += repository.getSmsExportState().subscribe { state ->
      store.update { it.copy(smsExportState = state) }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun setSmsDeliveryReportsEnabled(enabled: Boolean) {
    store.update { it.copy(smsDeliveryReportsEnabled = enabled) }
    SignalStore.settings().isSmsDeliveryReportsEnabled = enabled
  }

  fun setWifiCallingCompatibilityEnabled(enabled: Boolean) {
    store.update { it.copy(wifiCallingCompatibilityEnabled = enabled) }
    SignalStore.settings().isWifiCallingCompatibilityModeEnabled = enabled
  }

  fun checkSmsEnabled() {
    store.update { it.copy(useAsDefaultSmsApp = Util.isDefaultSmsProvider(ApplicationDependencies.getApplication())) }
  }
}
