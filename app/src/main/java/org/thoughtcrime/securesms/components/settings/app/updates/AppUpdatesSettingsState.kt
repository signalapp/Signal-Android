/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.updates

import kotlin.time.Duration

data class AppUpdatesSettingsState(
  val lastCheckedTime: Duration,
  val autoUpdateEnabled: Boolean
)
