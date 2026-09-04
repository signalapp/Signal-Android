/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import kotlinx.coroutines.withContext
import org.signal.appsettings.account.TwoFactorMethod
import org.signal.appsettings.totp.TotpApp
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.account.authenticator.TotpRepository
import org.thoughtcrime.securesms.components.settings.app.account.passkeys.AppPasskeysRepository
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.pin.SvrRepository
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

  private val totpRepository = TotpRepository()
  private val passkeysRepository = AppPasskeysRepository()

  fun hasPin(): Boolean = SignalStore.svr.hasPin() && !SignalStore.svr.hasOptedOut()

  fun hasRestoredAep(): Boolean = SignalStore.account.restoredAccountEntropyPool

  fun arePinRemindersEnabled(): Boolean = SignalStore.pin.arePinRemindersEnabled() && SignalStore.svr.hasPin()

  fun setPinRemindersEnabled(enabled: Boolean) = SignalStore.pin.setPinRemindersEnabled(enabled)

  fun isRegistrationLockEnabled(): Boolean = SignalStore.svr.isRegistrationLockEnabled

  fun isUserUnregistered(): Boolean = TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application)

  fun isClientDeprecated(): Boolean = SignalStore.misc.isClientDeprecated

  fun getPinKeyboardType(): PinKeyboardType = SignalStore.pin.keyboardType

  fun isPhoneNumberless(): Boolean = SignalStore.account.isPhoneNumberless

  fun getMaxTotpApps(): Int = totpRepository.getMaxApps()

  /**
   * Every second factor on the account, authenticator apps first, or a failure if we couldn't find out. Passkeys are
   * mocked for now, so only the authenticator apps can actually fail to load.
   */
  suspend fun getTwoFactorMethods(): TwoFactorMethodsResult {
    val apps = when (val result = totpRepository.getTotpApps()) {
      is TotpRepository.AppsResult.Success -> result.apps
      TotpRepository.AppsResult.NetworkFailure -> return TwoFactorMethodsResult.NetworkFailure
    }

    return TwoFactorMethodsResult.Success(apps.map { it.toTwoFactorMethod() } + passkeysRepository.getPasskeys())
  }

  /**
   * Removes an authenticator app from the account, returning whether it's gone. An app the service has already
   * forgotten counts as gone, since that's the outcome the user asked for.
   */
  suspend fun removeTotpApp(appId: Long): Boolean {
    return when (totpRepository.removeTotpApp(appId)) {
      TotpRepository.UpdateResult.Success, TotpRepository.UpdateResult.AppNotFound -> true
      TotpRepository.UpdateResult.NetworkFailure -> false
    }
  }

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

  private fun TotpApp.toTwoFactorMethod(): TwoFactorMethod {
    return TwoFactorMethod(id = id, kind = TwoFactorMethod.Kind.AUTHENTICATOR_APP, name = name, createdAt = createdAt)
  }

  sealed interface TwoFactorMethodsResult {
    data class Success(val methods: List<TwoFactorMethod>) : TwoFactorMethodsResult

    data object NetworkFailure : TwoFactorMethodsResult
  }
}
