/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.advancedprivacy

/**
 * One-shot side effects that have to be carried out by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface AdvancedPrivacySettingsAction {

  /** Open the sealed sender explainer in the browser. */
  data object OpenSealedSenderLearnMore : AdvancedPrivacySettingsAction

  /** Open the automatic key verification explainer in the browser. */
  data object OpenAutomaticKeyVerificationLearnMore : AdvancedPrivacySettingsAction
}
