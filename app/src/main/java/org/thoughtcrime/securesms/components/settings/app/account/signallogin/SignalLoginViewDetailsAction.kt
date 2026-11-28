/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.signallogin

import org.signal.core.util.censor

/**
 * One-shot side effects that need an Activity, and therefore have to be carried out by whatever is hosting the Signal
 * Login details screen rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface SignalLoginViewDetailsAction {

  /** Leave the screen. */
  data object NavigateBack : SignalLoginViewDetailsAction

  /** Launch the system credential manager UI so the user can store the login in their password manager. */
  data object LaunchSaveToPasswordManager : SignalLoginViewDetailsAction

  /** Tell the user that there is no password manager on the device to save the login into. */
  data object ShowNoPasswordManagerAvailable : SignalLoginViewDetailsAction

  /** Launch the system document picker so the user can choose where to save the login PDF. */
  data object LaunchSaveAsPdf : SignalLoginViewDetailsAction

  /** Open the help article on keeping the Signal Login safe. */
  data object OpenDoNotShareLearnMoreArticle : SignalLoginViewDetailsAction

  /** Copy the specified text to the clipboard */
  data class CopyTextToClipboard(val text: String) : SignalLoginViewDetailsAction {
    override fun toString(): String {
      return "CopyTextToClipboard(text=${text.censor()})"
    }
  }
}
