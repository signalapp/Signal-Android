/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.view

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.badges.Badge
import org.signal.core.ui.compose.PagerIndicators
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ViewBadgeSheetContentTest {

  private val composeTestRule = createComposeRule()

  @get:Rule
  val rules: RuleChain = RuleChain.outerRule(MockAppDependenciesRule()).around(composeTestRule)

  private val events = mutableListOf<ViewBadgeSheetEvent>()

  private val star = badge("star")
  private val boost = badge(Badge.BOOST_BADGE_ID)
  private val planet = badge("planet")

  private val state = ViewBadgeSheetState(badges = listOf(star, boost, planet), shortName = "Alex")

  @Test
  fun givenAStartBadge_whenIDisplayTheSheet_thenIExpectItsPage() {
    setContent(state.copy(startBadge = planet))

    composeTestRule.onNodeWithTag(PagerIndicators.testTagDot(2)).assertIsSelected()
    composeTestRule.onNodeWithTag(PagerIndicators.testTagDot(0)).assertIsNotSelected()
  }

  @Test
  fun whenIClickADot_thenIExpectItsPage() {
    setContent(state)

    composeTestRule.onNodeWithTag(PagerIndicators.testTagDot(1)).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(PagerIndicators.testTagDot(1)).assertIsSelected()
  }

  @Test
  fun whenISwipe_thenIExpectTheNextPage() {
    setContent(state)

    composeTestRule.onNodeWithTag(ViewBadgeSheetTestTags.PAGER).performTouchInput { swipeLeft() }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(PagerIndicators.testTagDot(1)).assertIsSelected()
  }

  @Test
  fun givenOneBadge_whenIDisplayTheSheet_thenIExpectNoDots() {
    setContent(state.copy(badges = listOf(star)))

    composeTestRule.onNodeWithTag(PagerIndicators.testTagDot(0)).assertDoesNotExist()
  }

  @Test
  fun whenIClickTheAction_thenIExpectActionClicked() {
    setContent(state)

    composeTestRule.onNodeWithTag(ViewBadgeSheetTestTags.BUTTON_ACTION).performClick()

    assertThat(events).containsExactly(ViewBadgeSheetEvent.ActionClicked)
  }

  @Test
  fun givenTheViewersOwnBadges_whenIDisplayTheSheet_thenIExpectNoAction() {
    setContent(state.copy(showAction = false))

    composeTestRule.onNodeWithTag(ViewBadgeSheetTestTags.BUTTON_ACTION).assertDoesNotExist()
  }

  @Test
  fun givenNoInAppDonations_whenIDisplayTheSheet_thenIExpectTheWebsiteButtonAndExplanation() {
    setContent(state.copy(canDonateInApp = false))

    composeTestRule.onNodeWithTag(ViewBadgeSheetTestTags.NO_IN_APP_DONATIONS).assertExists()
    composeTestRule.onNodeWithText("Donate to Signal").assertExists()
  }

  private fun setContent(state: ViewBadgeSheetState) {
    composeTestRule.setContent {
      ViewBadgeSheetContent(state = state, onEvent = { events += it })
    }
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
