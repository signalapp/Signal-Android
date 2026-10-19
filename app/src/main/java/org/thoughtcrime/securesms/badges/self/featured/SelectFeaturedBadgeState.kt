package org.thoughtcrime.securesms.badges.self.featured

import org.signal.core.models.badges.Badge

data class SelectFeaturedBadgeState(
  val stage: Stage = Stage.INIT,
  val selectedBadge: Badge? = null,
  val allUnlockedBadges: List<Badge> = listOf()
) {
  enum class Stage {
    INIT,
    READY,
    SAVING,
    SAVED
  }
}
