/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.badges

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.badges.Badge

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BadgesOverviewScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<BadgesOverviewEvent>()

  private val star = badge(id = "star", name = "Signal Star")
  private val planet = badge(id = "planet", name = "Signal Planet")

  private val ready = BadgesOverviewState(
    stage = BadgesOverviewState.Stage.READY,
    badges = listOf(star, planet),
    featuredBadge = star,
    displayBadgesOnProfile = true,
    hasUnexpiredBadges = true,
    hasInternet = true
  )

  @Test
  fun whenIClickABadge_thenIExpectBadgeClicked() {
    setContent(ready)

    composeTestRule.onNodeWithTag(BadgeGridTestTags.badge(planet)).performClick()

    assertThat(events).containsExactly(BadgesOverviewEvent.BadgeClicked(planet))
  }

  @Test
  fun whenIClickTheToggleAndFeaturedRow_thenIExpectTheirEvents() {
    setContent(ready)

    scrollTo(BadgesOverviewTestTags.TOGGLE_DISPLAY_BADGES)
    composeTestRule.onNodeWithTag(BadgesOverviewTestTags.TOGGLE_DISPLAY_BADGES).performClick()
    scrollTo(BadgesOverviewTestTags.ROW_FEATURED_BADGE)
    composeTestRule.onNodeWithTag(BadgesOverviewTestTags.ROW_FEATURED_BADGE).performClick()

    assertThat(events).containsExactly(
      BadgesOverviewEvent.DisplayBadgesOnProfileToggled(false),
      BadgesOverviewEvent.FeaturedBadgeClicked
    )
  }

  @Test
  fun givenAFeaturedBadge_whenIDisplayScreen_thenIExpectItsNameOnTheRow() {
    setContent(ready)

    scrollTo(BadgesOverviewTestTags.ROW_FEATURED_BADGE)
    composeTestRule.onNodeWithTag(BadgesOverviewTestTags.ROW_FEATURED_BADGE).assert(hasText("Signal Star"))
  }

  @Test
  fun givenNoInternet_whenIDisplayScreen_thenIExpectTheSettingsDisabled() {
    setContent(ready.copy(hasInternet = false))

    scrollTo(BadgesOverviewTestTags.ROW_FEATURED_BADGE)
    composeTestRule.onNodeWithTag(BadgesOverviewTestTags.TOGGLE_DISPLAY_BADGES).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(BadgesOverviewTestTags.ROW_FEATURED_BADGE).assertIsNotEnabled()
  }

  @Test
  fun givenAnUpdateInProgress_whenIDisplayScreen_thenIExpectTheToggleDisabled() {
    setContent(ready.copy(stage = BadgesOverviewState.Stage.UPDATING_BADGE_DISPLAY_STATE))

    scrollTo(BadgesOverviewTestTags.TOGGLE_DISPLAY_BADGES)
    composeTestRule.onNodeWithTag(BadgesOverviewTestTags.TOGGLE_DISPLAY_BADGES).assertIsNotEnabled()
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(BadgesOverviewTestTags.LIST).performScrollToNode(hasTestTag(testTag))
  }

  private fun setContent(state: BadgesOverviewState) {
    composeTestRule.setContent {
      BadgesOverviewScreen(state = state, onEvent = { events += it })
    }
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
