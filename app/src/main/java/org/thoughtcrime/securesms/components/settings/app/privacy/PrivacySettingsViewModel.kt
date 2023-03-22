package org.thoughtcrime.securesms.components.settings.app.privacy

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.livedata.Store

class PrivacySettingsViewModel(
  private val sharedPreferences: SharedPreferences,
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
    sharedPreferences.edit().putBoolean(TextSecurePreferences.READ_RECEIPTS_PREF, enabled).apply()
    repository.syncReadReceiptState()
    refresh()
  }

  fun setTypingIndicatorsEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.TYPING_INDICATORS, enabled).apply()
    repository.syncTypingIndicatorsState()
    refresh()
  }

  fun setScreenLockEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.SCREEN_LOCK, enabled).apply()
    refresh()
  }

  fun setScreenLockTimeout(seconds: Long) {
    TextSecurePreferences.setScreenLockTimeout(ApplicationDependencies.getApplication(), seconds)
    refresh()
  }

  fun setScreenSecurityEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.SCREEN_SECURITY_PREF, enabled).apply()
    refresh()
  }

  fun setIncognitoKeyboard(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.INCOGNITO_KEYBORAD_PREF, enabled).apply()
    refresh()
  }

  fun togglePaymentLock(enable: Boolean) {
    SignalStore.paymentsValues().paymentLock = enable
    refresh()
  }

  fun setObsoletePasswordTimeoutEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.PASSPHRASE_TIMEOUT_PREF, enabled).apply()
    refresh()
  }

  fun setObsoletePasswordTimeout(minutes: Int) {
    TextSecurePreferences.setPassphraseTimeoutInterval(ApplicationDependencies.getApplication(), minutes)
    refresh()
  }

  fun refresh() {
    store.update(this::updateState)
  }

  private fun getState(): PrivacySettingsState {
    return PrivacySettingsState(
      blockedCount = 0,
      readReceipts = TextSecurePreferences.isReadReceiptsEnabled(ApplicationDependencies.getApplication()),
      typingIndicators = TextSecurePreferences.isTypingIndicatorsEnabled(ApplicationDependencies.getApplication()),
      screenLock = TextSecurePreferences.isScreenLockEnabled(ApplicationDependencies.getApplication()),
      screenLockActivityTimeout = TextSecurePreferences.getScreenLockTimeout(ApplicationDependencies.getApplication()),
      screenSecurity = TextSecurePreferences.isScreenSecurityEnabled(ApplicationDependencies.getApplication()),
      incognitoKeyboard = TextSecurePreferences.isIncognitoKeyboardEnabled(ApplicationDependencies.getApplication()),
      paymentLock = SignalStore.paymentsValues().paymentLock,
      isObsoletePasswordEnabled = !TextSecurePreferences.isPasswordDisabled(ApplicationDependencies.getApplication()),
      isObsoletePasswordTimeoutEnabled = TextSecurePreferences.isPassphraseTimeoutEnabled(ApplicationDependencies.getApplication()),
      obsoletePasswordTimeout = TextSecurePreferences.getPassphraseTimeoutInterval(ApplicationDependencies.getApplication()),
      universalExpireTimer = SignalStore.settings().universalExpireTimer
    )
  }

  private fun updateState(state: PrivacySettingsState): PrivacySettingsState {
    return getState().copy(blockedCount = state.blockedCount)
  }

  class Factory(
    private val sharedPreferences: SharedPreferences,
    private val repository: PrivacySettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(PrivacySettingsViewModel(sharedPreferences, repository)))
    }
  }
}
