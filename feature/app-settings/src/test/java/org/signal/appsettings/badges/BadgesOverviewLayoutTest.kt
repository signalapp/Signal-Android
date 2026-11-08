/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.badges

import android.net.Uri
import org.junit.Test
import org.signal.core.models.badges.Badge
import org.signal.core.ui.SignalLayoutStressTest

class BadgesOverviewLayoutTest : SignalLayoutStressTest() {

  private val badges = listOf(
    badge(id = "star", name = "Signal Star"),
    badge(id = "planet", name = "Signal Planet"),
    badge(id = "heart", name = "Signal Heart"),
    badge(id = "rocket", name = "Signal Rocket"),
    badge(id = "boost", name = "Signal Boost", expirationTimestamp = 1)
  )

  private val ready = BadgesOverviewState(
    stage = BadgesOverviewState.Stage.READY,
    badges = badges,
    featuredBadge = badges.first(),
    displayBadgesOnProfile = true,
    hasUnexpiredBadges = true,
    hasInternet = true
  )

  @Test
  fun `empty`() = assertPassesLayoutChecks {
    BadgesOverviewScreen(state = BadgesOverviewState(), onEvent = {})
  }

  @Test
  fun `populated`() = assertPassesLayoutChecks {
    BadgesOverviewScreen(state = ready, onEvent = {})
  }

  @Test
  fun `updating`() = assertPassesLayoutChecks {
    BadgesOverviewScreen(state = ready.copy(stage = BadgesOverviewState.Stage.UPDATING_BADGE_DISPLAY_STATE), onEvent = {})
  }

  @Test
  fun `offline`() = assertPassesLayoutChecks {
    BadgesOverviewScreen(state = ready.copy(hasInternet = false), onEvent = {})
  }

  private fun badge(id: String, name: String, expirationTimestamp: Long = Long.MAX_VALUE): Badge {
    return Badge(
      id = id,
      category = Badge.Category.Donor,
      name = name,
      description = "",
      imageUrl = Uri.EMPTY,
      imageDensity = "xxxhdpi",
      expirationTimestamp = expirationTimestamp,
      visible = true,
      duration = 0
    )
  }
}
