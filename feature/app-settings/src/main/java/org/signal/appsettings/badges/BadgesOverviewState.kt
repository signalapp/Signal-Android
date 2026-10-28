/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.badges

import org.signal.core.models.badges.Badge

data class BadgesOverviewState(
  val stage: Stage = Stage.INIT,
  /** Every badge the user has unlocked, expired ones included. */
  val badges: List<Badge> = emptyList(),
  val featuredBadge: Badge? = null,
  val displayBadgesOnProfile: Boolean = false,
  /** The badge of a monthly donation that's been cancelled and runs out at the end of the period, drawn faded. */
  val fadedBadgeId: String? = null,
  val hasUnexpiredBadges: Boolean = false,
  val hasInternet: Boolean = false,
  val snackbar: Snackbar = Snackbar.None
) {

  val canChangeSettings: Boolean
    get() = stage == Stage.READY && hasUnexpiredBadges && hasInternet

  override fun toString(): String {
    return "BadgesOverviewState(stage=$stage, badgeCount=${badges.size}, featuredBadge=${featuredBadge?.id}, displayBadgesOnProfile=$displayBadgesOnProfile, fadedBadgeId=$fadedBadgeId, hasUnexpiredBadges=$hasUnexpiredBadges, hasInternet=$hasInternet, snackbar=$snackbar)"
  }

  enum class Stage {
    /** The user's badges haven't been read yet. */
    INIT,
    READY,
    UPDATING_BADGE_DISPLAY_STATE
  }

  sealed interface Snackbar {
    data object None : Snackbar

    /** Showing or hiding badges on the profile didn't reach the service. */
    data object FailedToUpdateProfile : Snackbar
  }
}
