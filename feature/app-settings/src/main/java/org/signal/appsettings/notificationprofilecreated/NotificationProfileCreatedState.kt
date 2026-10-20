/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.notificationprofilecreated

/**
 * [NotificationProfileCreatedScreen] state.
 *
 * This is logged, so be sure `toString()` contains nothing sensitive.
 */
data class NotificationProfileCreatedState(
  /** Whether the new profile turns on and off on a schedule, or null until the profile has been read. */
  val hasSchedule: Boolean? = null
)
