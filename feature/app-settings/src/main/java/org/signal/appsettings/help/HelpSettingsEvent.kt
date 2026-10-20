/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface HelpSettingsEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : HelpSettingsEvent

  /** The user tapped the support center row. */
  data object SupportCenterClicked : HelpSettingsEvent

  /** The user tapped the contact us row. */
  data object ContactUsClicked : HelpSettingsEvent

  /** The user long-pressed the version row. */
  data object VersionLongClicked : HelpSettingsEvent

  /** The user tapped the debug log row. */
  data object DebugLogClicked : HelpSettingsEvent

  /** The user tapped the licenses row. */
  data object LicensesClicked : HelpSettingsEvent

  /** The user tapped the terms and privacy policy row. */
  data object TermsAndPrivacyPolicyClicked : HelpSettingsEvent
}
