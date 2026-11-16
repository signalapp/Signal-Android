/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.view

sealed interface ViewBadgeSheetEvent {
  /** The user tapped the button inviting them to donate. */
  data object ActionClicked : ViewBadgeSheetEvent
}
