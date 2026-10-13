package org.thoughtcrime.securesms.components.settings.app.privacy

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.livedata.Store

class PrivacySettingsViewModel(
  private val repository: PrivacySettingsRepository
) : ViewModel() {

  private val store = Store(getState())

  val state: LiveData<PrivacySettingsState> = store.stateLiveData

  fun refreshBlockedCount() {
    repository.getBlockedCount { count ->
      store.update { it.copy(blockedCount = count) }
      refresh()
    }
  }

  fun setReadReceiptsEnabled(enabled: Boolean) {
    SignalStore.settings.isReadReceiptsEnabled = enabled
    repository.syncReadReceiptState()
    refresh()
  }

  fun setTypingIndicatorsEnabled(enabled: Boolean) {
    SignalStore.settings.isTypingIndicatorsEnabled = enabled
    repository.syncTypingIndicatorsState()
    refresh()
  }

  fun setScreenSecurityEnabled(enabled: Boolean) {
    SignalStore.settings.isScreenSecurityEnabled = enabled
    refresh()
  }

  fun setIncognitoKeyboard(enabled: Boolean) {
    SignalStore.settings.isIncognitoKeyboardEnabled = enabled
    refresh()
  }

  fun togglePaymentLock(enable: Boolean) {
    SignalStore.payments.paymentLock = enable
    refresh()
  }

  fun setObsoletePasswordTimeoutEnabled(enabled: Boolean) {
    SignalStore.settings.passphraseTimeoutEnabled = enabled
    refresh()
  }

  fun setObsoletePasswordTimeout(minutes: Int) {
    SignalStore.settings.passphraseTimeout = minutes
    refresh()
  }

  fun refresh() {
    store.update(this::updateState)
  }

  private fun getState(): PrivacySettingsState {
    return PrivacySettingsState(
      hasPhoneNumber = !SignalStore.account.isPhoneNumberless,
      blockedCount = 0,
      readReceipts = SignalStore.settings.isReadReceiptsEnabled,
      typingIndicators = SignalStore.settings.isTypingIndicatorsEnabled,
      screenLock = SignalStore.settings.screenLockEnabled,
      screenLockActivityTimeout = SignalStore.settings.screenLockTimeout,
      screenSecurity = SignalStore.settings.isScreenSecurityEnabled,
      incognitoKeyboard = SignalStore.settings.isIncognitoKeyboardEnabled,
      paymentLock = SignalStore.payments.paymentLock,
      isObsoletePasswordEnabled = !SignalStore.settings.passphraseDisabled,
      isObsoletePasswordTimeoutEnabled = SignalStore.settings.passphraseTimeoutEnabled,
      obsoletePasswordTimeout = SignalStore.settings.passphraseTimeout,
      universalExpireTimer = SignalStore.settings.universalExpireTimer
    )
  }

  private fun updateState(state: PrivacySettingsState): PrivacySettingsState {
    return getState().copy(blockedCount = state.blockedCount)
  }

  class Factory(
    private val repository: PrivacySettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(PrivacySettingsViewModel(repository)))
    }
  }
}
