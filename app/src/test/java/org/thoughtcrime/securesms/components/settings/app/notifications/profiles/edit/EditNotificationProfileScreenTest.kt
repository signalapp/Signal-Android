/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.edit

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.emoji.EmojiDependenciesRule

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EditNotificationProfileScreenTest {

  private val composeTestRule = createComposeRule()

  /** The emoji button and presets render their emoji with Emojifier. */
  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(EmojiDependenciesRule(ApplicationProvider.getApplicationContext()))
    .around(composeTestRule)

  private val events = mutableListOf<EditNotificationProfileEvent>()

  @Test
  fun `create mode asks for a name and offers to create`() {
    setContent(EditNotificationProfileState(createMode = true, isLoaded = true))

    composeTestRule.onNodeWithText("Name your profile").assertExists()
    composeTestRule.onNodeWithText("Create").assertExists()
  }

  @Test
  fun `edit mode offers to save`() {
    setContent(EditNotificationProfileState(createMode = false, isLoaded = true))

    composeTestRule.onNodeWithText("Edit this profile").assertExists()
    composeTestRule.onNodeWithText("Save").assertExists()
  }

  @Test
  fun `the button is disabled until the profile loads`() {
    setContent(EditNotificationProfileState(createMode = false))

    composeTestRule.onNodeWithTag(EditNotificationProfileTestTags.BUTTON_SAVE).assertIsNotEnabled()
  }

  @Test
  fun `clicking save emits SaveClicked`() {
    setContent(EditNotificationProfileState(isLoaded = true))

    composeTestRule.onNodeWithTag(EditNotificationProfileTestTags.BUTTON_SAVE).performClick()

    assertThat(events).containsExactly(EditNotificationProfileEvent.SaveClicked)
  }

  @Test
  fun `clicking a preset emits PresetClicked with its name and emoji`() {
    setContent(EditNotificationProfileState(isLoaded = true))

    composeTestRule.onNodeWithTag(EditNotificationProfileTestTags.preset(1)).performClick()

    assertThat(events).containsExactly(EditNotificationProfileEvent.PresetClicked(name = "Sleep", emoji = "😴"))
  }

  @Test
  fun `the clear button only shows with something to clear and emits ClearClicked`() {
    setContent(EditNotificationProfileState(isLoaded = true, emoji = "💪"))

    composeTestRule.onNodeWithTag(EditNotificationProfileTestTags.BUTTON_CLEAR).performClick()

    assertThat(events).containsExactly(EditNotificationProfileEvent.ClearClicked)
  }

  @Test
  fun `the clear button is hidden with nothing to clear`() {
    setContent(EditNotificationProfileState(isLoaded = true))

    composeTestRule.onNodeWithTag(EditNotificationProfileTestTags.BUTTON_CLEAR).assertDoesNotExist()
  }

  @Test
  fun `name errors are shown`() {
    setContent(EditNotificationProfileState(isLoaded = true, nameError = EditNotificationProfileState.NameError.Empty))
    composeTestRule.onNodeWithText("Must have a name").assertExists()
  }

  @Test
  fun `a duplicate name is called out`() {
    setContent(EditNotificationProfileState(isLoaded = true, nameError = EditNotificationProfileState.NameError.Duplicate))
    composeTestRule.onNodeWithText("A profile with this name already exists").assertExists()
  }

  @Test
  fun `the count appears as the name nears its limit`() {
    setContent(EditNotificationProfileState(isLoaded = true, name = "a".repeat(21)))
    composeTestRule.onNodeWithText("21/32").assertDoesNotExist()
  }

  @Test
  fun `the count shows at 22 characters`() {
    setContent(EditNotificationProfileState(isLoaded = true, name = "a".repeat(22)))
    composeTestRule.onNodeWithText("22/32").assertExists()
  }

  @Test
  fun `typing emits NameChanged`() {
    setContent(EditNotificationProfileState(isLoaded = true))

    composeTestRule.onNodeWithTag(EditNotificationProfileTestTags.NAME_FIELD).performTextInput("W")

    assertThat(events).containsExactly(EditNotificationProfileEvent.NameChanged("W"))
  }

  @Test
  fun `clicking the emoji button emits EmojiButtonClicked`() {
    setContent(EditNotificationProfileState(isLoaded = true))

    composeTestRule.onNodeWithTag(EditNotificationProfileTestTags.BUTTON_EMOJI).performClick()

    assertThat(events).containsExactly(EditNotificationProfileEvent.EmojiButtonClicked)
  }

  @Test
  fun `the emoji and clear buttons are labeled`() {
    setContent(EditNotificationProfileState(isLoaded = true, emoji = "💪"))

    composeTestRule.onNodeWithContentDescription("Choose emoji").performClick()
    composeTestRule.onNodeWithContentDescription("Clear name and emoji").performClick()

    assertThat(events).containsExactly(EditNotificationProfileEvent.EmojiButtonClicked, EditNotificationProfileEvent.ClearClicked)
  }

  @Test
  @Config(qualifiers = "w360dp-h360dp")
  fun `the title moves into the toolbar once the header scrolls away`() {
    setContent(EditNotificationProfileState(isLoaded = true))
    composeTestRule.onAllNodesWithText("Name your profile").assertCountEquals(1)

    composeTestRule.onNodeWithTag(EditNotificationProfileTestTags.LIST).performScrollToNode(hasTestTag(EditNotificationProfileTestTags.preset(4)))

    // The header has scrolled out of the list, so the one left is the toolbar's.
    composeTestRule.onAllNodesWithText("Name your profile").assertCountEquals(1)
    composeTestRule.onNodeWithTag(EditNotificationProfileTestTags.LIST).onChildren().filterToOne(hasText("Name your profile")).assertDoesNotExist()
  }

  @Test
  fun `the open picker reports picks as EmojiSelected`() {
    composeTestRule.setContent {
      EditNotificationProfileScreen(
        state = EditNotificationProfileState(isLoaded = true, showEmojiPicker = true),
        onEvent = { events += it },
        emojiPicker = { onEmojiSelected ->
          TextButton(onClick = { onEmojiSelected("\uD83D\uDD25") }) {
            Text("Pick")
          }
        }
      )
    }

    composeTestRule.onNodeWithText("Pick").performClick()

    assertThat(events).containsExactly(EditNotificationProfileEvent.EmojiSelected("\uD83D\uDD25"))
  }

  @Test
  fun `the picker is closed unless the state says otherwise`() {
    composeTestRule.setContent {
      EditNotificationProfileScreen(
        state = EditNotificationProfileState(isLoaded = true),
        onEvent = { events += it },
        emojiPicker = { Text("Pick") }
      )
    }

    composeTestRule.onNodeWithText("Pick").assertDoesNotExist()
  }

  @Test
  fun `clicking back emits NavigateBackClicked`() {
    setContent(EditNotificationProfileState(isLoaded = true))

    composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

    assertThat(events).containsExactly(EditNotificationProfileEvent.NavigateBackClicked)
  }

  private fun setContent(state: EditNotificationProfileState) {
    composeTestRule.setContent {
      EditNotificationProfileScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }
}
