/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.expired

sealed interface ExpiredBadgeSheetEvent {
  /** The user tapped the button to boost, or to become a sustainer. */
  data object DonateClicked : ExpiredBadgeSheetEvent

  /** The user tapped "Not now". */
  data object NotNowClicked : ExpiredBadgeSheetEvent
}
