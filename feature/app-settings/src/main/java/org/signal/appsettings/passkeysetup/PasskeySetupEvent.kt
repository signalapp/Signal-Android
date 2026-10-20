/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.passkeysetup

import org.signal.passwordmanager.PasskeyCreationResult

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface PasskeySetupEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : PasskeySetupEvent

  /** The user tapped a learn more link. */
  data object LearnMoreClicked : PasskeySetupEvent

  /** The user tapped the button that starts creating a passkey. */
  data object SetUpPasskeyClicked : PasskeySetupEvent

  /** The passkey ceremony finished, one way or another. A credential still has to be verified by the service to count. */
  data class PasskeyCeremonyCompleted(val result: PasskeyCreationResult) : PasskeySetupEvent

  /** Dismisses whatever is in [PasskeySetupState.dialog]. */
  data object DialogDismissed : PasskeySetupEvent
}
