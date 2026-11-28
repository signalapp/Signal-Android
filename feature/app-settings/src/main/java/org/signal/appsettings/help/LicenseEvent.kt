/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface LicenseEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : LicenseEvent
}
