/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.phonenumberprivacy

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface PhoneNumberPrivacySettingsEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : PhoneNumberPrivacySettingsEvent

  /** The user picked "Everyone" under who can see my number. */
  data object EveryoneCanSeeMyNumberClicked : PhoneNumberPrivacySettingsEvent

  /** The user picked "Nobody" under who can see my number. */
  data object NobodyCanSeeMyNumberClicked : PhoneNumberPrivacySettingsEvent

  /** The user picked "Everyone" under who can find me by number. */
  data object EveryoneCanFindMeByNumberClicked : PhoneNumberPrivacySettingsEvent

  /** The user picked "Nobody" under who can find me by number. Only allowed once nobody can see their number. */
  data object NobodyCanFindMeByNumberClicked : PhoneNumberPrivacySettingsEvent

  /** The user confirmed that nobody should be able to find them by number. */
  data object NobodyCanFindMeByNumberConfirmed : PhoneNumberPrivacySettingsEvent

  /** Dismisses whatever is in [PhoneNumberPrivacySettingsState.dialog]. */
  data object DialogDismissed : PhoneNumberPrivacySettingsEvent

  /** Whatever was in [PhoneNumberPrivacySettingsState.snackbar] was shown and dismissed. */
  data object SnackbarDismissed : PhoneNumberPrivacySettingsEvent
}
