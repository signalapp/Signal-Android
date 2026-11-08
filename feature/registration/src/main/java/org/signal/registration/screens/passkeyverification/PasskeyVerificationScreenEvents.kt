/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.passkeyverification

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed class PasskeyVerificationScreenEvents {

  /** The user tapped the continue button to verify with their passkey. */
  data object ContinueClicked : PasskeyVerificationScreenEvents()

  /** The user tapped the cancel button. */
  data object CancelClicked : PasskeyVerificationScreenEvents()

  /** The passkey ceremony the host ran produced nothing usable, for a reason the user can do nothing about. */
  data object PasskeyCeremonyFailed : PasskeyVerificationScreenEvents()

  /** The user acknowledged the passkey error. */
  data object PasskeyErrorDismissed : PasskeyVerificationScreenEvents()
}
