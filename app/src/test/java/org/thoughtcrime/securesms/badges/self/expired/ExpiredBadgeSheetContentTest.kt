/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.expired

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.badges.Badge
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExpiredBadgeSheetContentTest {

  private val composeTestRule = createComposeRule()

  @get:Rule
  val rules: RuleChain = RuleChain.outerRule(MockAppDependenciesRule()).around(composeTestRule)

  private val events = mutableListOf<ExpiredBadgeSheetEvent>()

  @Test
  fun whenIClickEachButton_thenIExpectTheirEvents() {
    setContent(ExpiredBadgeSheetState(badge = badge(Badge.BOOST_BADGE_ID)))

    composeTestRule.onNodeWithTag(ExpiredBadgeSheetTestTags.BUTTON_DONATE).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(ExpiredBadgeSheetTestTags.BUTTON_NOT_NOW).performScrollTo().performClick()

    assertThat(events).containsExactly(ExpiredBadgeSheetEvent.DonateClicked, ExpiredBadgeSheetEvent.NotNowClicked)
  }

  @Test
  fun givenAnExpiredBoost_whenIDisplayTheSheet_thenIExpectToBeOfferedASubscription() {
    setContent(ExpiredBadgeSheetState(badge = badge(Badge.BOOST_BADGE_ID)))

    composeTestRule.onNodeWithText("Boost Badge Expired").assertExists()
    composeTestRule.onNodeWithText("Become a Sustainer").assertExists()
  }

  @Test
  fun givenALikelySustainer_whenIDisplayTheSheet_thenIExpectToBeOfferedABoost() {
    setContent(ExpiredBadgeSheetState(badge = badge(Badge.BOOST_BADGE_ID), isLikelyASustainer = true))

    composeTestRule.onNodeWithText("Add a Boost").assertExists()
  }

  @Test
  fun givenAMonthlyBadge_whenIDisplayTheSheet_thenIExpectTheCancelledTitle() {
    setContent(ExpiredBadgeSheetState(badge = badge("star")))

    composeTestRule.onNodeWithText("Monthly Donation Cancelled").assertExists()
  }

  private fun setContent(state: ExpiredBadgeSheetState) {
    composeTestRule.setContent {
      ExpiredBadgeSheetContent(state = state, onEvent = { events += it })
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
      expirationTimestamp = 1,
      visible = true,
      duration = 0
    )
  }
}
