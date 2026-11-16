/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.signallogin

import androidx.annotation.StringRes

/**
 * Whichever snackbar the settings Signal Login details screen is showing, if any.
 */
sealed interface SettingsSignalLoginDetailsSnackbar {
  data object None : SettingsSignalLoginDetailsSnackbar

  /** There is no password manager on the device to save the login into. */
  data object NoPasswordManagerAvailable : SettingsSignalLoginDetailsSnackbar

  /** Saving the login as a PDF didn't work, for the reason [messageRes] gives. */
  data class SaveAsPdfFailed(@StringRes val messageRes: Int) : SettingsSignalLoginDetailsSnackbar
}
