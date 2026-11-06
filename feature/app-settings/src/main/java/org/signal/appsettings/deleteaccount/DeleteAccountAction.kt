/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.deleteaccount

/**
 * One-shot side effects that need an Activity, and therefore have to be carried out by the host rather than the screen
 * itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface DeleteAccountAction {

  /** Open the system settings for this app, which is where the user can clear its data by hand. */
  data object LaunchAppSettings : DeleteAccountAction
}
