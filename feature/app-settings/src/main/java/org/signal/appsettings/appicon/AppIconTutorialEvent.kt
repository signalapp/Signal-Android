/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appicon

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface AppIconTutorialEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : AppIconTutorialEvent
}
