/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

/**
 * One-shot side effects that need an Activity, and therefore have to be carried out
 * by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface HelpSettingsAction {

  /** Open the support center in the browser. */
  data object OpenSupportCenter : HelpSettingsAction

  /** Copy [versionName] to the clipboard. */
  data class CopyVersionToClipboard(val versionName: String) : HelpSettingsAction

  /** Open the terms and privacy policy in the browser. */
  data object OpenTermsAndPrivacyPolicy : HelpSettingsAction
}
