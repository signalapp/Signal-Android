/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.expired

import android.net.Uri
import org.junit.Rule
import org.junit.Test
import org.signal.core.models.badges.Badge
import org.signal.core.ui.SignalLayoutStressTest
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule

class ExpiredBadgeSheetContentLayoutTest : SignalLayoutStressTest() {

  /** Badge images load through Glide, which reads its HTTP client out of AppDependencies. */
  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @Test
  fun `expired boost`() = assertPassesLayoutChecks {
    ExpiredBadgeSheetContent(state = ExpiredBadgeSheetState(badge = badge(Badge.BOOST_BADGE_ID)), onEvent = {})
  }

  @Test
  fun `expired boost for a likely sustainer`() = assertPassesLayoutChecks {
    ExpiredBadgeSheetContent(state = ExpiredBadgeSheetState(badge = badge(Badge.BOOST_BADGE_ID), isLikelyASustainer = true), onEvent = {})
  }

  @Test
  fun `cancelled monthly donation`() = assertPassesLayoutChecks {
    ExpiredBadgeSheetContent(state = ExpiredBadgeSheetState(badge = badge("star")), onEvent = {})
  }

  private fun badge(id: String): Badge {
    return Badge(
      id = id,
      category = Badge.Category.Donor,
      name = id,
      description = "",
      imageUrl = Uri.EMPTY,
      imageDensity = "xxxhdpi",
      expirationTimestamp = 1,
      visible = true,
      duration = 0
    )
  }
}
