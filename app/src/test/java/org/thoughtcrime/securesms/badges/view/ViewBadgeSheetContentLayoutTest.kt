/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.view

import android.net.Uri
import org.junit.Rule
import org.junit.Test
import org.signal.core.models.badges.Badge
import org.signal.core.ui.SignalLayoutStressTest
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule

class ViewBadgeSheetContentLayoutTest : SignalLayoutStressTest() {

  /** Badge images load through Glide, which reads its HTTP client out of AppDependencies. */
  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  /** The name is the recipient's, so a long one can't help being split once it outgrows the screen. */
  private val userText = setOf(ViewBadgeSheetTestTags.badgeName(0), ViewBadgeSheetTestTags.badgeName(1), ViewBadgeSheetTestTags.badgeName(2))

  private val state = ViewBadgeSheetState(
    badges = listOf(badge("star"), badge(Badge.BOOST_BADGE_ID), badge("planet")),
    shortName = "Maximiliana"
  )

  @Test
  fun `several badges`() = assertPassesLayoutChecks(ignoreTags = userText) {
    ViewBadgeSheetContent(state = state, onEvent = {})
  }

  @Test
  fun `one badge`() = assertPassesLayoutChecks(ignoreTags = userText) {
    ViewBadgeSheetContent(state = state.copy(badges = state.badges.take(1)), onEvent = {})
  }

  @Test
  fun `no in-app donations`() = assertPassesLayoutChecks(ignoreTags = userText) {
    ViewBadgeSheetContent(state = state.copy(canDonateInApp = false), onEvent = {})
  }

  @Test
  fun `the viewer's own badges`() = assertPassesLayoutChecks(ignoreTags = userText) {
    ViewBadgeSheetContent(state = state.copy(showAction = false), onEvent = {})
  }

  private fun badge(id: String): Badge {
    return Badge(
      id = id,
      category = Badge.Category.Donor,
      name = id,
      description = "",
      imageUrl = Uri.EMPTY,
      imageDensity = "xxxhdpi",
      expirationTimestamp = Long.MAX_VALUE,
      visible = true,
      duration = 0
    )
  }
}
