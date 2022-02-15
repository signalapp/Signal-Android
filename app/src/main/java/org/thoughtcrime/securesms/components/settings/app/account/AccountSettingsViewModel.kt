package org.thoughtcrime.securesms.components.settings.app.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.livedata.Store

class AccountSettingsViewModel : ViewModel() {
  private val store: Store<AccountSettingsState> = Store(getCurrentState())

  val state: LiveData<AccountSettingsState> = store.stateLiveData

  fun refreshState() {
    store.update { getCurrentState() }
  }

  private fun getCurrentState(): AccountSettingsState {
    return AccountSettingsState(
      hasPin = SignalStore.kbsValues().hasPin() && !SignalStore.kbsValues().hasOptedOut(),
      pinRemindersEnabled = SignalStore.pinValues().arePinRemindersEnabled(),
      registrationLockEnabled = SignalStore.kbsValues().isV2RegistrationLockEnabled
    )
  }
}
