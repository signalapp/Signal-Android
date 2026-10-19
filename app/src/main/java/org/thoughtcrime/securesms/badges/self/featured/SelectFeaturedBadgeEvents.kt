/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.featured

import org.thoughtcrime.securesms.badges.models.Badge

sealed interface SelectFeaturedBadgeEvents {
  /** User has selected a badge to feature */
  data class BadgeSelected(val badge: Badge) : SelectFeaturedBadgeEvents

  /** User has saved */
  data object Save : SelectFeaturedBadgeEvents
}
