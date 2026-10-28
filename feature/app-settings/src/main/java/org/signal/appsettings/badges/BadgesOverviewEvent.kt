/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.badges

import org.signal.core.models.badges.Badge

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface BadgesOverviewEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : BadgesOverviewEvent

  /** The user tapped one of their badges. */
  data class BadgeClicked(val badge: Badge) : BadgesOverviewEvent {
    override fun toString(): String = "BadgeClicked(id=${badge.id})"
  }

  /** The user flipped the toggle for showing badges on their profile. */
  data class DisplayBadgesOnProfileToggled(val enabled: Boolean) : BadgesOverviewEvent

  /** The user tapped the featured badge row. */
  data object FeaturedBadgeClicked : BadgesOverviewEvent

  /** The sheet showing [BadgesOverviewState.viewedBadge] was closed. */
  data object BadgeSheetDismissed : BadgesOverviewEvent

  /** Whatever was in [BadgesOverviewState.snackbar] has gone. */
  data object SnackbarDismissed : BadgesOverviewEvent
}
