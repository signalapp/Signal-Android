/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

/**
 * One-shot side effects that need an Activity, and therefore have to be carried out by whatever hosts
 * [BackupsSettingsScreen] rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface BackupsSettingsAction {

  /** Start the backups checkout flow, which runs in its own activity. */
  data object LaunchCheckout : BackupsSettingsAction

  /** Send the user to the support article at [url]. */
  data class OpenSupportArticle(val url: String) : BackupsSettingsAction
}
