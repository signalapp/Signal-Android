/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.twofactorverify

/**
 * One-shot side effects that need an activity, and therefore have to be carried out by whatever hosts
 * [TwoFactorVerifyScreen] rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface TwoFactorVerifyAction {

  /** Run the passkey provider's sheet, then report back with [TwoFactorVerifyEvent.PasskeyCeremonyCompleted]. */
  data class AuthenticateWithPasskey(val parameters: PasskeyAssertionParameters) : TwoFactorVerifyAction

  /** The user proved they still hold [method], so the flow is done. */
  data class Verified(val method: TwoFactorVerifyMethod) : TwoFactorVerifyAction

  /** Leave the flow without verifying anything. */
  data object Close : TwoFactorVerifyAction
}
