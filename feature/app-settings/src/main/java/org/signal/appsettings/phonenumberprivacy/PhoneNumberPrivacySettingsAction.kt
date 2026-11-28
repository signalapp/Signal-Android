/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.phonenumberprivacy

/**
 * One-shot side effects that have to be carried out by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface PhoneNumberPrivacySettingsAction {

  /** Leave the screen. */
  data object NavigateBack : PhoneNumberPrivacySettingsAction
}
