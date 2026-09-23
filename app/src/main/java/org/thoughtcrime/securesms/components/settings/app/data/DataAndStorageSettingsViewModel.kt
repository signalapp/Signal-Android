package org.thoughtcrime.securesms.components.settings.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.PlayServicesUtil
import org.signal.mediasend.SentMediaQuality
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SettingsValues.ForceWebsocketMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messages.IncomingMessageObserver
import org.thoughtcrime.securesms.webrtc.CallDataMode

class DataAndStorageSettingsViewModel(
  private val repository: DataAndStorageSettingsRepository
) : ViewModel() {

  private val store = MutableStateFlow(getState())

  val state: StateFlow<DataAndStorageSettingsState> = store

  fun refresh() {
    repository.getTotalStorageUse { totalStorageUse ->
      store.update { getState().copy(totalStorageUse = totalStorageUse, showStayConnectedDialog = it.showStayConnectedDialog) }
    }
  }

  fun setMobileAutoDownloadValues(resultSet: Set<String>) {
    SignalStore.settings.mobileMediaDownloadAllowed = resultSet
    getStateAndCopyStorageUsage()
  }

  fun setWifiAutoDownloadValues(resultSet: Set<String>) {
    SignalStore.settings.wifiMediaDownloadAllowed = resultSet
    getStateAndCopyStorageUsage()
  }

  fun setRoamingAutoDownloadValues(resultSet: Set<String>) {
    SignalStore.settings.roamingMediaDownloadAllowed = resultSet
    getStateAndCopyStorageUsage()
  }

  fun setCallDataMode(callDataMode: CallDataMode) {
    SignalStore.settings.callDataMode = callDataMode
    AppDependencies.signalCallManager.dataModeUpdate()
    getStateAndCopyStorageUsage()
  }

  fun setSentMediaQuality(sentMediaQuality: SentMediaQuality) {
    SignalStore.settings.sentMediaQuality = sentMediaQuality
    getStateAndCopyStorageUsage()
  }

  fun onForceWebsocketModeToggled(enabled: Boolean) {
    if (enabled) {
      store.update { it.copy(showStayConnectedDialog = true) }
    } else {
      applyForceWebsocketMode(false)
    }
  }

  fun confirmStayConnectedInBackground() {
    applyForceWebsocketMode(true)
  }

  fun dismissStayConnectedInBackgroundDialog() {
    store.update { it.copy(showStayConnectedDialog = false) }
  }

  private fun applyForceWebsocketMode(enabled: Boolean) {
    SignalStore.settings.forceWebsocketMode = if (enabled) ForceWebsocketMode.ENABLED_BY_USER else ForceWebsocketMode.DISABLED
    if (!enabled) {
      IncomingMessageObserver.stopForegroundService(AppDependencies.application)
    }
    AppDependencies.resetNetwork()
    AppDependencies.startNetwork()
    getStateAndCopyStorageUsage()
  }

  private fun getStateAndCopyStorageUsage() {
    store.update { getState().copy(totalStorageUse = it.totalStorageUse, showStayConnectedDialog = it.showStayConnectedDialog) }
  }

  private fun getState() = DataAndStorageSettingsState(
    totalStorageUse = 0,
    mobileAutoDownloadValues = SignalStore.settings.mobileMediaDownloadAllowed,
    wifiAutoDownloadValues = SignalStore.settings.wifiMediaDownloadAllowed,
    roamingAutoDownloadValues = SignalStore.settings.roamingMediaDownloadAllowed,
    callDataMode = SignalStore.settings.callDataMode,
    isProxyEnabled = SignalStore.proxy.isProxyEnabled,
    sentMediaQuality = SignalStore.settings.sentMediaQuality,
    forceWebsocketMode = SignalStore.settings.forceWebsocketMode.isEnabled,
    playServicesAvailable = PlayServicesUtil.getPlayServicesStatus(AppDependencies.application) == PlayServicesUtil.PlayServicesStatus.SUCCESS,
    showStayConnectedDialog = false
  )

  class Factory(
    private val repository: DataAndStorageSettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(DataAndStorageSettingsViewModel(repository)))
    }
  }
}
