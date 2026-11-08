/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

/**
 * One-shot side effects that need an Activity or the nav graph, and therefore have to be carried out
 * by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface HelpSettingsAction {

  /** Leave the screen. */
  data object NavigateBack : HelpSettingsAction

  /** Open the support center in the browser. */
  data object OpenSupportCenter : HelpSettingsAction

  /** Open the screen for contacting support. */
  data object NavigateToContactUs : HelpSettingsAction

  /** Copy [versionName] to the clipboard and tell the user it was copied. */
  data class CopyVersionToClipboard(val versionName: String) : HelpSettingsAction

  /** Open the debug log submission screen. */
  data object NavigateToDebugLog : HelpSettingsAction

  /** Open the open-source licenses screen. */
  data object NavigateToLicenses : HelpSettingsAction

  /** Open the terms and privacy policy in the browser. */
  data object OpenTermsAndPrivacyPolicy : HelpSettingsAction
}
