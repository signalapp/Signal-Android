/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.account

data class AccountSettingsState(
  val hasPin: Boolean = false,
  val hasRestoredAep: Boolean = false,
  val pinRemindersEnabled: Boolean = false,
  val registrationLockEnabled: Boolean = false,
  val userUnregistered: Boolean = false,
  val clientDeprecated: Boolean = false,
  val canTransferWhileUnregistered: Boolean = true,
  val isPhoneNumberless: Boolean = false,
  val signalLogin: SignalLogin? = null,
  val dialog: Dialog = Dialog.None
) {

  val isNotDeprecatedOrUnregistered: Boolean
    get() = !(userUnregistered || clientDeprecated)

  /**
   * The Signal Login and two-factor authentication sections, which only exist for phone-numberless accounts. Null means
   * the sections aren't shown at all.
   */
  data class SignalLogin(
    /** The second factors on the account, which only mean anything once [loadState] is [LoadState.LOADED]. */
    val twoFactorMethods: List<TwoFactorMethod> = emptyList(),
    /** How the last look at the account went, which decides what the two-factor list shows in place of rows. */
    val loadState: LoadState = LoadState.LOADING,
    /** How many authenticator apps the account is allowed to have at once. */
    val maxTotpApps: Int = 0
  ) {

    val atMaxTotpApps: Boolean
      get() = twoFactorMethods.count { it.kind == TwoFactorMethod.Kind.AUTHENTICATOR_APP } >= maxTotpApps
  }

  /** How the last attempt to read the account's second factors went, since an empty list can't say on its own. */
  enum class LoadState {
    /** We haven't heard back about the account yet. */
    LOADING,

    LOADED,

    /** We couldn't reach the service, which is worth another try. */
    NETWORK_FAILURE
  }

  /** Whichever dialog the screen is showing, if any. Only one is ever up at a time. */
  sealed interface Dialog {
    data object None : Dialog

    /** Confirms wiping all app data, which is all a deprecated or unregistered client can do. */
    data object ConfirmDeleteAllData : Dialog

    /**
     * Collects the PIN, which the user has to get right before we'll stop reminding them of it.
     * [canSubmit] is decided by the view model, which owns the minimum PIN length.
     */
    data class ConfirmPinToDisableReminders(
      val pin: String = "",
      val isAlphanumericKeyboard: Boolean = false,
      val incorrectPin: Boolean = false,
      val canSubmit: Boolean = false
    ) : Dialog {
      override fun toString(): String = "ConfirmPinToDisableReminders(incorrectPin=$incorrectPin)"
    }

    /** Confirms turning registration lock on or off, then spins while we tell the server. */
    data class ConfirmRegistrationLock(
      val enable: Boolean,
      val inProgress: Boolean = false
    ) : Dialog

    /** Confirms removing [appId], which still has to be backed up by a code from the app itself. */
    data class ConfirmRemoveTotpApp(val appId: Long) : Dialog

    /** Explains that the account already has as many authenticator apps as it's allowed. */
    data object MaxTotpAppsReached : Dialog
  }
}
