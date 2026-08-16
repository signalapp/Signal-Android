/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.account.authenticator.AuthenticatorRepository
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.pin.SvrRepository
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.kbs.PinHashUtil
import java.io.IOException

/**
 * All of the storage and network access behind [AccountSettingsViewModel].
 */
class AccountSettingsRepository {

  companion object {
    private val TAG = Log.tag(AccountSettingsRepository::class)
  }

  private val authenticatorRepository = AuthenticatorRepository()

  fun hasPin(): Boolean = SignalStore.svr.hasPin() && !SignalStore.svr.hasOptedOut()

  fun hasRestoredAep(): Boolean = SignalStore.account.restoredAccountEntropyPool

  fun arePinRemindersEnabled(): Boolean = SignalStore.pin.arePinRemindersEnabled() && SignalStore.svr.hasPin()

  fun setPinRemindersEnabled(enabled: Boolean) = SignalStore.pin.setPinRemindersEnabled(enabled)

  fun isRegistrationLockEnabled(): Boolean = SignalStore.svr.isRegistrationLockEnabled

  fun isUserUnregistered(): Boolean = TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application)

  fun isClientDeprecated(): Boolean = SignalStore.misc.isClientDeprecated

  fun getPinKeyboardType(): PinKeyboardType = SignalStore.pin.keyboardType

  fun isPhoneNumberlessRegistrationEnabled(): Boolean = Environment.PHONENUMBERLESS_REGISTRATION

  fun hasAuthenticatorApp(): Boolean = authenticatorRepository.hasAuthenticatorApp()

  fun verifyLocalPin(pin: String): Boolean {
    val localPinHash = SignalStore.svr.localPinHash
    if (localPinHash == null) {
      Log.w(TAG, "No local PIN hash to verify against!")
      return false
    }

    return PinHashUtil.verifyLocalPinHash(localPinHash, pin)
  }

  /**
   * Turns registration lock on or off on the service, returning whether it worked.
   */
  suspend fun setRegistrationLockEnabled(enabled: Boolean): Boolean = withContext(SignalDispatchers.IO) {
    try {
      if (enabled) {
        SvrRepository.enableRegistrationLockForUserWithPin()
      } else {
        SvrRepository.disableRegistrationLockForUserWithPin()
      }
      true
    } catch (e: IOException) {
      Log.w(TAG, "Failed to ${if (enabled) "enable" else "disable"} registration lock.", e)
      false
    }
  }
}
