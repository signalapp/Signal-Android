/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.passkeyverification

/**
 * Everything [PasskeyVerificationScreen] needs to render.
 */
data class PasskeyVerificationState(
  /** Whether to tell the user their passkey ceremony didn't work, leaving them here to try again. */
  val showPasskeyError: Boolean = false
)
