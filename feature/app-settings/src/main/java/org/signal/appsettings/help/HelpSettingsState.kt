/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

/**
 * [HelpSettingsScreen] state.
 *
 * This is logged, so be sure `toString()` contains nothing sensitive.
 */
data class HelpSettingsState(
  /** The installed app version, shown on the version row and copied when it is long-pressed. */
  val versionName: String = ""
)
