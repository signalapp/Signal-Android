/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.updates

import kotlin.time.Duration

/**
 * [AppUpdatesSettingsScreen] state.
 *
 * This is logged, so be sure `toString()` contains nothing sensitive.
 */
data class AppUpdatesSettingsState(
  /** When we last successfully checked for an update, or [Duration.ZERO] if we never have. */
  val lastCheckedTime: Duration = Duration.ZERO,
  /** Whether updates are downloaded and installed without asking. */
  val autoUpdateEnabled: Boolean = false,
  /** Whether this device can install updates without user interaction, which is what makes automatic updates possible. */
  val isAutoUpdateSupported: Boolean = false
)
