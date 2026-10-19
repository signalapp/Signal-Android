package org.thoughtcrime.securesms.badges.view

import org.signal.core.models.badges.Badge
import org.thoughtcrime.securesms.recipients.Recipient

data class ViewBadgeState(
  val allBadgesVisibleOnProfile: List<Badge> = listOf(),
  val badgeLoadState: LoadState = LoadState.INIT,
  val selectedBadge: Badge? = null,
  val recipient: Recipient? = null
) {
  enum class LoadState {
    INIT,
    LOADED
  }
}
