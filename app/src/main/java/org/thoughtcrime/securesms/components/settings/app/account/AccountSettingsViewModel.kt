package org.thoughtcrime.securesms.components.settings.app.account

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.TextSecurePreferences

class AccountSettingsViewModel : ViewModel() {
  private val store: MutableStateFlow<AccountSettingsState> = MutableStateFlow(getCurrentState())

  val state: StateFlow<AccountSettingsState> = store

  fun refreshState() {
    store.update { getCurrentState() }
  }

  fun togglePinKeyboardType() {
    store.update {
      it.copy(pinKeyboardType = it.pinKeyboardType.other)
    }
  }

  private fun getCurrentState(): AccountSettingsState {
    return AccountSettingsState(
      hasPin = SignalStore.svr.hasPin() && !SignalStore.svr.hasOptedOut(),
      pinKeyboardType = SignalStore.pin.keyboardType,
      hasRestoredAep = SignalStore.account.restoredAccountEntropyPool,
      pinRemindersEnabled = SignalStore.pin.arePinRemindersEnabled() && SignalStore.svr.hasPin(),
      registrationLockEnabled = SignalStore.svr.isRegistrationLockEnabled,
      userUnregistered = TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application),
      clientDeprecated = SignalStore.misc.isClientDeprecated,
      canTransferWhileUnregistered = true
    )
  }
}
