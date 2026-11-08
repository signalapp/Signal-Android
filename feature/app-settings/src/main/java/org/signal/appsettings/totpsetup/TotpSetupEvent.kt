/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.totpsetup

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface TotpSetupEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : TotpSetupEvent

  /** The user tapped the button that hands the setup key off to their authenticator app. */
  data object OpenTotpAppClicked : TotpSetupEvent

  /** The user tapped the button that copies the setup key. */
  data object CopyKeyClicked : TotpSetupEvent

  /** The fragment reported that no installed app could handle the setup link. */
  data object NoTotpAppFound : TotpSetupEvent

  /** The user finished the steps and is ready to enter a code. */
  data object ContinueClicked : TotpSetupEvent

  /** Dismisses whatever is in [TotpSetupState.dialog], which leaves the screen since none of them are recoverable here. */
  data object DialogDismissed : TotpSetupEvent
}
