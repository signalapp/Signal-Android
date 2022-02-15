package org.thoughtcrime.securesms.components.settings.app.internal

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.signal.ringrtc.CallManager
import org.thoughtcrime.securesms.keyvalue.InternalValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.livedata.Store

class InternalSettingsViewModel(private val repository: InternalSettingsRepository) : ViewModel() {
  private val preferenceDataStore = SignalStore.getPreferenceDataStore()

  private val store = Store(getState())

  init {
    repository.getEmojiVersionInfo { version ->
      store.update { it.copy(emojiVersion = version) }
    }
  }

  val state: LiveData<InternalSettingsState> = store.stateLiveData

  fun setSeeMoreUserDetails(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.RECIPIENT_DETAILS, enabled)
    refresh()
  }

  fun setShakeToReport(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.SHAKE_TO_REPORT, enabled)
    refresh()
  }

  fun setDisableStorageService(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.DISABLE_STORAGE_SERVICE, enabled)
    refresh()
  }

  fun setGv2DoNotCreateGv2Groups(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.GV2_DO_NOT_CREATE_GV2, enabled)
    refresh()
  }

  fun setGv2ForceInvites(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.GV2_FORCE_INVITES, enabled)
    refresh()
  }

  fun setGv2IgnoreServerChanges(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.GV2_IGNORE_SERVER_CHANGES, enabled)
    refresh()
  }

  fun setGv2IgnoreP2PChanges(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.GV2_IGNORE_P2P_CHANGES, enabled)
    refresh()
  }

  fun setDisableAutoMigrationInitiation(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.GV2_DISABLE_AUTOMIGRATE_INITIATION, enabled)
    refresh()
  }

  fun setDisableAutoMigrationNotification(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.GV2_DISABLE_AUTOMIGRATE_NOTIFICATION, enabled)
    refresh()
  }

  fun setForceCensorship(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.FORCE_CENSORSHIP, enabled)
    refresh()
  }

  fun setUseBuiltInEmoji(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.FORCE_BUILT_IN_EMOJI, enabled)
    refresh()
  }

  fun setRemoveSenderKeyMinimum(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.REMOVE_SENDER_KEY_MINIMUM, enabled)
    refresh()
  }

  fun setDelayResends(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.DELAY_RESENDS, enabled)
    refresh()
  }

  fun setInternalGroupCallingServer(server: String?) {
    preferenceDataStore.putString(InternalValues.CALLING_SERVER, server)
    refresh()
  }

  fun setInternalAudioProcessingMethod(method: CallManager.AudioProcessingMethod) {
    preferenceDataStore.putInt(InternalValues.AUDIO_PROCESSING_METHOD, method.ordinal)
    refresh()
  }

  private fun refresh() {
    store.update { getState().copy(emojiVersion = it.emojiVersion) }
  }

  private fun getState() = InternalSettingsState(
    seeMoreUserDetails = SignalStore.internalValues().recipientDetails(),
    shakeToReport = SignalStore.internalValues().shakeToReport(),
    gv2doNotCreateGv2Groups = SignalStore.internalValues().gv2DoNotCreateGv2Groups(),
    gv2forceInvites = SignalStore.internalValues().gv2ForceInvites(),
    gv2ignoreServerChanges = SignalStore.internalValues().gv2IgnoreServerChanges(),
    gv2ignoreP2PChanges = SignalStore.internalValues().gv2IgnoreP2PChanges(),
    disableAutoMigrationInitiation = SignalStore.internalValues().disableGv1AutoMigrateInitiation(),
    disableAutoMigrationNotification = SignalStore.internalValues().disableGv1AutoMigrateNotification(),
    forceCensorship = SignalStore.internalValues().forcedCensorship(),
    callingServer = SignalStore.internalValues().groupCallingServer(),
    audioProcessingMethod = SignalStore.internalValues().audioProcessingMethod(),
    useBuiltInEmojiSet = SignalStore.internalValues().forceBuiltInEmoji(),
    emojiVersion = null,
    removeSenderKeyMinimium = SignalStore.internalValues().removeSenderKeyMinimum(),
    delayResends = SignalStore.internalValues().delayResends(),
    disableStorageService = SignalStore.internalValues().storageServiceDisabled()
  )

  class Factory(private val repository: InternalSettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(InternalSettingsViewModel(repository)))
    }
  }
}
