/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.list

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotificationProfilesScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<NotificationProfilesEvent>()

  @Test
  fun `nothing is shown while profiles load`() {
    setContent(NotificationProfilesState())

    composeTestRule.onNodeWithTag(NotificationProfilesTestTags.BUTTON_CREATE_PROFILE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(NotificationProfilesTestTags.LIST).assertDoesNotExist()
  }

  @Test
  fun `the empty state's create button emits NewProfileClicked`() {
    setContent(NotificationProfilesState(profiles = emptyList()))

    composeTestRule.onNodeWithTag(NotificationProfilesTestTags.BUTTON_CREATE_PROFILE).performClick()

    assertThat(events).containsExactly(NotificationProfilesEvent.NewProfileClicked)
  }

  @Test
  fun `the new profile row emits NewProfileClicked`() {
    setContent(NotificationProfilesState(profiles = listOf(profile(1, "Work"))))

    composeTestRule.onNodeWithTag(NotificationProfilesTestTags.ROW_NEW_PROFILE).performClick()

    assertThat(events).containsExactly(NotificationProfilesEvent.NewProfileClicked)
  }

  @Test
  fun `clicking a profile emits ProfileClicked`() {
    setContent(NotificationProfilesState(profiles = listOf(profile(2, "Sleep"), profile(1, "Work"))))

    scrollTo(NotificationProfilesTestTags.profile(1))
    composeTestRule.onNodeWithTag(NotificationProfilesTestTags.profile(1)).performClick()

    assertThat(events).containsExactly(NotificationProfilesEvent.ProfileClicked(1))
  }

  @Test
  fun `each profile's name is shown`() {
    setContent(NotificationProfilesState(profiles = listOf(profile(2, "Sleep"), profile(1, "Work"))))

    composeTestRule.onNodeWithText("Sleep").assertIsDisplayed()
    composeTestRule.onNodeWithText("Work").assertIsDisplayed()
  }

  @Test
  fun `only the active profile shows how long it stays on`() {
    setContent(
      NotificationProfilesState(
        profiles = listOf(profile(2, "Sleep"), profile(1, "Work")),
        activeProfileId = 2,
        activeStatus = NotificationProfiles.ActiveStatus.On
      )
    )

    composeTestRule.onNodeWithText("On").assertIsDisplayed()
  }

  @Test
  fun `clicking back emits NavigateBackClicked`() {
    setContent(NotificationProfilesState(profiles = emptyList()))

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(events).containsExactly(NotificationProfilesEvent.NavigateBackClicked)
  }

  private fun setContent(state: NotificationProfilesState) {
    composeTestRule.setContent {
      NotificationProfilesScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(NotificationProfilesTestTags.LIST)
      .performScrollToNode(hasTestTag(testTag))
  }

  private fun profile(id: Long, name: String): NotificationProfile {
    return NotificationProfile(
      id = id,
      name = name,
      emoji = "",
      createdAt = id,
      schedule = NotificationProfileSchedule(id = id),
      notificationProfileId = NotificationProfileId(UUID(0L, id))
    )
  }
}
