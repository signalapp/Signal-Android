/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.badges

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
class SelectFeaturedBadgeScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<SelectFeaturedBadgeEvent>()

  private val star = badge("star")
  private val planet = badge("planet")

  private val ready = SelectFeaturedBadgeState(
    stage = SelectFeaturedBadgeState.Stage.READY,
    selectedBadge = star,
    badges = listOf(star, planet)
  )

  @Test
  fun whenIClickABadgeAndSave_thenIExpectTheirEvents() {
    setContent(ready)

    composeTestRule.onNodeWithTag(BadgeGridTestTags.badge(planet)).performClick()
    composeTestRule.onNodeWithTag(SelectFeaturedBadgeTestTags.BUTTON_SAVE).performClick()

    assertThat(events).containsExactly(SelectFeaturedBadgeEvent.BadgeClicked(planet), SelectFeaturedBadgeEvent.SaveClicked)
  }

  @Test
  fun givenASelectedBadge_whenIDisplayScreen_thenIExpectOnlyItSelected() {
    setContent(ready)

    composeTestRule.onNodeWithTag(BadgeGridTestTags.badge(star)).assertIsSelected()
    composeTestRule.onNodeWithTag(BadgeGridTestTags.badge(planet)).assertIsNotSelected()
  }

  @Test
  fun givenASelectedBadge_whenIDisplayScreen_thenIExpectSaveEnabled() {
    setContent(ready)

    composeTestRule.onNodeWithTag(SelectFeaturedBadgeTestTags.BUTTON_SAVE).assertIsEnabled()
  }

  @Test
  fun givenNoSelectedBadge_whenIDisplayScreen_thenIExpectSaveDisabled() {
    setContent(ready.copy(selectedBadge = null))

    composeTestRule.onNodeWithTag(SelectFeaturedBadgeTestTags.BUTTON_SAVE).assertIsNotEnabled()
  }

  @Test
  fun givenASaveInProgress_whenIDisplayScreen_thenIExpectSaveDisabled() {
    setContent(ready.copy(stage = SelectFeaturedBadgeState.Stage.SAVING))

    composeTestRule.onNodeWithTag(SelectFeaturedBadgeTestTags.BUTTON_SAVE).assertIsNotEnabled()
  }

  private fun setContent(state: SelectFeaturedBadgeState) {
    composeTestRule.setContent {
      SelectFeaturedBadgeScreen(state = state, onEvent = { events += it })
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
