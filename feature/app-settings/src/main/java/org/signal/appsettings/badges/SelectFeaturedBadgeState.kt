/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.badges

import org.signal.core.models.badges.Badge

data class SelectFeaturedBadgeState(
  val stage: Stage = Stage.INIT,
  val selectedBadge: Badge? = null,
  /** The user's badges that haven't expired, which are the only ones they can feature. */
  val badges: List<Badge> = emptyList(),
  val snackbar: Snackbar = Snackbar.None
) {

  val canSave: Boolean
    get() = stage == Stage.READY && selectedBadge != null

  override fun toString(): String {
    return "SelectFeaturedBadgeState(stage=$stage, selectedBadge=${selectedBadge?.id}, badgeCount=${badges.size}, snackbar=$snackbar)"
  }

  enum class Stage {
    /** The user's badges haven't been read yet. */
    INIT,
    READY,
    SAVING
  }

  sealed interface Snackbar {
    data object None : Snackbar

    /** The featured badge couldn't be saved to the profile. */
    data object FailedToUpdateProfile : Snackbar
  }
}
