/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.advancedprivacy

/**
 * [AdvancedPrivacySettingsScreen] state.
 *
 * This is logged, so be sure `toString()` contains nothing sensitive.
 */
data class AdvancedPrivacySettingsState(
  /** Calls are relayed through the Signal server to hide the user's IP address. */
  val alwaysRelayCalls: Boolean = false,
  /** Whether the censorship circumvention toggle can be changed, and why. */
  val censorshipCircumventionState: CensorshipCircumventionState = CensorshipCircumventionState.AVAILABLE,
  /** Whether censorship circumvention is in effect, including when it was turned on automatically. */
  val censorshipCircumventionEnabled: Boolean = false,
  /** Message details mark messages that were delivered with sealed sender. */
  val showSealedSenderStatusIcon: Boolean = false,
  /** Sealed sender is accepted from people who aren't contacts. */
  val allowSealedSenderFromAnyone: Boolean = false,
  /** Signal tries to verify the encryption of 1:1 chats automatically. */
  val allowAutomaticKeyVerification: Boolean = false,
  /** Only the primary device can change who may send with sealed sender. */
  val isPrimaryDevice: Boolean = false
)

/**
 * Whether the user can change censorship circumvention, and the reason shown under the toggle.
 */
enum class CensorshipCircumventionState(val available: Boolean) {
  /** The setting is unavailable because you're connected to the websocket. */
  UNAVAILABLE_CONNECTED(false),

  /** The setting is unavailable because you have no network access at all. */
  UNAVAILABLE_NO_INTERNET(false),

  /** The setting is available, and the user manually disabled it even though we thought they were censored. */
  AVAILABLE_MANUALLY_DISABLED(true),

  /** The setting is available, and it's on because we think the user is censored. */
  AVAILABLE_AUTOMATICALLY_ENABLED(true),

  /** The setting is generically available. */
  AVAILABLE(true)
}
