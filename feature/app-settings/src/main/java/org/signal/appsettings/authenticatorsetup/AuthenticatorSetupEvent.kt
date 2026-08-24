/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.authenticatorsetup

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface AuthenticatorSetupEvent {

  /** The user tapped the navigation (close) icon. */
  data object NavigateBackClicked : AuthenticatorSetupEvent

  /** The user tapped the button that hands the setup key off to their authenticator app. */
  data object OpenAuthenticatorAppClicked : AuthenticatorSetupEvent

  /** The user tapped the button that copies the setup key. */
  data object CopyKeyClicked : AuthenticatorSetupEvent

  /** The fragment reported that no installed app could handle the setup link. */
  data object NoAuthenticatorAppFound : AuthenticatorSetupEvent

  /** The user finished the steps and is ready to enter a code. */
  data object ContinueClicked : AuthenticatorSetupEvent
}
