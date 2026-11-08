/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.badges

import android.net.Uri
import org.junit.Test
import org.signal.core.models.badges.Badge
import org.signal.core.ui.SignalLayoutStressTest

class SelectFeaturedBadgeLayoutTest : SignalLayoutStressTest() {

  private val badges = listOf(
    badge(id = "star", name = "Signal Star"),
    badge(id = "planet", name = "Signal Planet"),
    badge(id = "heart", name = "Signal Heart"),
    badge(id = "rocket", name = "Signal Rocket"),
    badge(id = "sun", name = "Signal Sun")
  )

  private val ready = SelectFeaturedBadgeState(
    stage = SelectFeaturedBadgeState.Stage.READY,
    selectedBadge = badges[1],
    badges = badges
  )

  @Test
  fun `loading`() = assertPassesLayoutChecks {
    SelectFeaturedBadgeScreen(state = SelectFeaturedBadgeState(), onEvent = {})
  }

  @Test
  fun `populated`() = assertPassesLayoutChecks {
    SelectFeaturedBadgeScreen(state = ready, onEvent = {})
  }

  @Test
  fun `saving`() = assertPassesLayoutChecks {
    SelectFeaturedBadgeScreen(state = ready.copy(stage = SelectFeaturedBadgeState.Stage.SAVING), onEvent = {})
  }

  private fun badge(id: String, name: String): Badge {
    return Badge(
      id = id,
      category = Badge.Category.Donor,
      name = name,
      description = "",
      imageUrl = Uri.EMPTY,
      imageDensity = "xxxhdpi",
      expirationTimestamp = Long.MAX_VALUE,
      visible = true,
      duration = 0
    )
  }
}
