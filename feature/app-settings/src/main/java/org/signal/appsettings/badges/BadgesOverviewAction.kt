/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.badges

import org.signal.core.models.badges.Badge

/**
 * One-shot side effects the host carries out for [BadgesOverviewScreen].
 */
sealed interface BadgesOverviewAction {

  /** Show [badge] in the sheet describing the user's badges. */
  data class ShowBadge(val badge: Badge) : BadgesOverviewAction {
    override fun toString(): String = "ShowBadge(id=${badge.id})"
  }

  /** Show the sheet explaining that [badge] has expired, or will once its cancelled donation runs out. */
  data class ShowExpiredBadge(val badge: Badge) : BadgesOverviewAction {
    override fun toString(): String = "ShowExpiredBadge(id=${badge.id})"
  }
}
