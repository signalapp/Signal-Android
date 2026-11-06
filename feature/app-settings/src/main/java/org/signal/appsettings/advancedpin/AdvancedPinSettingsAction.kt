/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.advancedpin

/**
 * One-shot side effects that need an Activity, and therefore have to be carried out by the host rather than the
 * screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface AdvancedPinSettingsAction {

  /** Ask the user to confirm turning their PIN off, and turn it off if they do. */
  data object ConfirmPinOptOut : AdvancedPinSettingsAction

  /** Open the flow for creating a PIN. */
  data object LaunchPinCreation : AdvancedPinSettingsAction

  /** Open payments so the user can record their recovery phrase. */
  data object LaunchRecoveryPhraseRecording : AdvancedPinSettingsAction
}
