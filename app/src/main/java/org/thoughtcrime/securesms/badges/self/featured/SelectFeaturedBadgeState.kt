package org.thoughtcrime.securesms.badges.self.featured

import org.thoughtcrime.securesms.badges.models.Badge

data class SelectFeaturedBadgeState(
  val selectedBadge: Badge? = null,
  val allUnlockedBadges: List<Badge> = listOf()
)
