/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.vector

import android.app.Application
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.Avatars
import org.thoughtcrime.securesms.avatar.compose.AvatarColorGridTestTags

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VectorAvatarCreationScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val avatar = Avatar.Vector("avatar_abstract_01", Avatars.colors[1], Avatar.DatabaseId.NotSet)

  @Test
  fun `when done is clicked, DoneClicked is emitted`() {
    val events = setContent()

    composeTestRule.onNodeWithTag(VectorAvatarCreationTestTags.DONE_BUTTON).performClick()

    assertThat(events).containsExactly(VectorAvatarCreationEvent.DoneClicked)
  }

  @Test
  fun `when a color is clicked, ColorSelected is emitted with that color`() {
    val events = setContent()

    composeTestRule.onAllNodesWithTag(AvatarColorGridTestTags.SWATCH)[5].performScrollTo().performClick()

    assertThat(events).containsExactly(VectorAvatarCreationEvent.ColorSelected(Avatars.colors[5]))
  }

  @Test
  fun `the avatar's color is the only one selected`() {
    setContent()

    composeTestRule.onNodeWithTag(VectorAvatarCreationTestTags.SCROLL_CONTAINER).performScrollToNode(hasTestTag(AvatarColorGridTestTags.SWATCH))
    val swatches = composeTestRule.onAllNodesWithTag(AvatarColorGridTestTags.SWATCH)
    swatches[1].assertIsSelected()
    swatches[0].assertIsNotSelected()
    swatches[2].assertIsNotSelected()
  }

  private fun setContent(): List<VectorAvatarCreationEvent> {
    val events = mutableListOf<VectorAvatarCreationEvent>()
    composeTestRule.setContent {
      SignalTheme(incognitoKeyboardEnabled = false) {
        VectorAvatarCreationScreen(
          state = VectorAvatarCreationState(avatar),
          onEvent = { events += it }
        )
      }
    }
    return events
  }
}
