/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.twofactorselection

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed class TwoFactorSelectionScreenEvents {

  /** The user tapped one of the two-factor method cards. */
  data class MethodSelected(val method: TwoFactorMethod) : TwoFactorSelectionScreenEvents()

  /** The user tapped the cancel button. */
  data object CancelClicked : TwoFactorSelectionScreenEvents()

  /** The passkey ceremony the host ran produced nothing usable, for a reason the user can do nothing about. */
  data object PasskeyCeremonyFailed : TwoFactorSelectionScreenEvents()

  /** The user acknowledged the passkey error. */
  data object PasskeyErrorDismissed : TwoFactorSelectionScreenEvents()
}
