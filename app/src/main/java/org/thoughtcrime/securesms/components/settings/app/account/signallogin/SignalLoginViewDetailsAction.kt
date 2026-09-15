/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.signallogin

import org.signal.core.util.censor

/**
 * One-shot side effects that need an Activity or the nav graph, and therefore have to be carried out by whatever is
 * hosting the Signal Login details screen rather than the screen itself.
 *
 * Hosts that can't reset the recovery key collect [Shared] instead, so they never have to handle an action they can't
 * produce.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface SignalLoginViewDetailsAction {

  /** The actions every host of the screen can produce. */
  sealed interface Shared : SignalLoginViewDetailsAction

  /** Leave the screen. */
  data object NavigateBack : Shared

  /** Launch the system credential manager UI so the user can store the login in their password manager. */
  data object LaunchSaveToPasswordManager : Shared

  /** Launch the system document picker so the user can choose where to save the login PDF. */
  data object LaunchSaveAsPdf : Shared

  /** Copy the specified text to the clipboard */
  data class CopyTextToClipboard(val text: String) : Shared {
    override fun toString(): String {
      return "CopyTextToClipboard(text=${text.censor()})"
    }
  }

  /** Hand the user off to the screen that generates and confirms their replacement recovery key. */
  data object LaunchRecoveryKeyReset : SignalLoginViewDetailsAction
}
