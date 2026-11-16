/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.text

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.compose.TabRowTestTags
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.Avatars
import org.thoughtcrime.securesms.avatar.compose.AvatarColorGridTestTags

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TextAvatarCreationScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val avatar = Avatar.Text("AB", Avatars.colors[1], Avatar.DatabaseId.NotSet)

  @Test
  fun `when done is clicked, DoneClicked is emitted`() {
    val events = setContent(TextAvatarCreationState(avatar))

    composeTestRule.onNodeWithTag(TextAvatarCreationTestTags.DONE_BUTTON).performClick()

    assertThat(events).containsExactly(TextAvatarCreationEvent.DoneClicked)
  }

  @Test
  fun `when text is typed, TextChanged is emitted with the new text`() {
    val events = setContent(TextAvatarCreationState(avatar))

    composeTestRule.onNodeWithTag(TextAvatarCreationTestTags.TEXT_INPUT).performTextReplacement("XYZ")

    assertThat(events.last()).isEqualTo(TextAvatarCreationEvent.TextChanged("XYZ"))
  }

  @Test
  fun `typed text is cut to three characters`() {
    val events = setContent(TextAvatarCreationState(avatar))

    composeTestRule.onNodeWithTag(TextAvatarCreationTestTags.TEXT_INPUT).performTextReplacement("WXYZ")

    assertThat(events.last()).isEqualTo(TextAvatarCreationEvent.TextChanged("WXY"))
  }

  @Test
  fun `when the color tab is clicked, TabClicked is emitted for it`() {
    val events = setContent(TextAvatarCreationState(avatar))

    composeTestRule.onNodeWithTag(TabRowTestTags.tab(1)).performClick()

    assertThat(events).contains(TextAvatarCreationEvent.TabClicked(TextAvatarCreationState.Tab.COLOR))
  }

  @Test
  fun `on the text tab, the colors are hidden`() {
    setContent(TextAvatarCreationState(avatar))

    composeTestRule.onNodeWithTag(TabRowTestTags.tab(0)).assertIsSelected()
    assertThat(composeTestRule.onAllNodesWithTag(AvatarColorGridTestTags.SWATCH).fetchSemanticsNodes().size).isEqualTo(0)
  }

  @Test
  fun `on the color tab, a color can be picked and the text can't be edited`() {
    val events = setContent(TextAvatarCreationState(avatar, TextAvatarCreationState.Tab.COLOR))

    composeTestRule.onNodeWithTag(TextAvatarCreationTestTags.TEXT_INPUT).assertIsNotEnabled()
    composeTestRule.onAllNodesWithTag(AvatarColorGridTestTags.SWATCH)[3].performScrollTo().performClick()

    assertThat(events).containsExactly(TextAvatarCreationEvent.ColorSelected(Avatars.colors[3]))
  }

  private fun setContent(state: TextAvatarCreationState): List<TextAvatarCreationEvent> {
    val events = mutableListOf<TextAvatarCreationEvent>()
    composeTestRule.setContent {
      SignalTheme(incognitoKeyboardEnabled = false) {
        TextAvatarCreationScreen(
          state = state,
          onEvent = { events += it }
        )
      }
    }
    return events
  }
}
