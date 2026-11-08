/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.badges

import org.signal.core.models.badges.Badge

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface SelectFeaturedBadgeEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : SelectFeaturedBadgeEvent

  /** The user tapped one of their badges. */
  data class BadgeClicked(val badge: Badge) : SelectFeaturedBadgeEvent {
    override fun toString(): String = "BadgeClicked(id=${badge.id})"
  }

  /** The user tapped Save. */
  data object SaveClicked : SelectFeaturedBadgeEvent

  /** Whatever was in [SelectFeaturedBadgeState.snackbar] has gone. */
  data object SnackbarDismissed : SelectFeaturedBadgeEvent
}
