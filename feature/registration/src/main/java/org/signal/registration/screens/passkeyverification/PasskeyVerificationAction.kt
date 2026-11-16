/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.passkeyverification

import org.signal.registration.screens.twofactorselection.WebAuthnParameters

/**
 * One-shot side effects that have to be carried out by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface PasskeyVerificationAction {

  /** The user wants to authenticate with their passkey. The host should run the credential-manager flow. */
  data class AuthenticateWithPasskey(val parameters: WebAuthnParameters) : PasskeyVerificationAction
}
