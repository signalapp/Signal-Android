/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.about

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.emoji.EmojiDependenciesRule

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EditAboutScreenTest {

  companion object {
    private const val EMOJI_PICKER = "emoji-picker"
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  /** The emoji button and presets render with Emojifier. */
  @get:Rule
  val emojiDependencies = EmojiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<EditAboutEvent>()

  @Test
  fun whenITypeAnAbout_thenIExpectAboutChangedEvent() {
    setContent(EditAboutState())

    input().performTextReplacement("Out to lunch")

    assertThat(events).contains(EditAboutEvent.AboutChanged("Out to lunch"))
  }

  @Test
  fun whenIClickTheEmojiButton_thenIExpectEmojiButtonClickedEvent() {
    setContent(EditAboutState())

    composeTestRule.onNodeWithTag(EditAboutTestTags.BUTTON_EMOJI).performClick()

    assertThat(events).contains(EditAboutEvent.EmojiButtonClicked)
  }

  @Test
  fun givenTheEmojiPickerIsOpen_whenItReportsAPick_thenIExpectEmojiSelectedEvent() {
    composeTestRule.setContent {
      EditAboutScreen(
        state = EditAboutState(showEmojiPicker = true),
        onEvent = { events += it },
        emojiPicker = { onEmojiSelected ->
          Text(
            text = "☕",
            modifier = Modifier
              .testTag(EMOJI_PICKER)
              .clickable { onEmojiSelected("☕") }
          )
        }
      )
    }

    composeTestRule.onNodeWithTag(EMOJI_PICKER).performClick()

    assertThat(events).contains(EditAboutEvent.EmojiSelected("☕"))
  }

  @Test
  fun givenTheEmojiPickerIsClosed_whenIDisplayScreen_thenIExpectNoPicker() {
    composeTestRule.setContent {
      EditAboutScreen(
        state = EditAboutState(),
        onEvent = { events += it },
        emojiPicker = { Text(text = "☕", modifier = Modifier.testTag(EMOJI_PICKER)) }
      )
    }

    composeTestRule.onNodeWithTag(EMOJI_PICKER).assertDoesNotExist()
  }

  @Test
  fun whenIClickClear_thenIExpectClearClickedEvent() {
    setContent(EditAboutState(about = "Speak freely", emoji = "👋"))

    composeTestRule.onNodeWithTag(EditAboutTestTags.BUTTON_CLEAR).performClick()

    assertThat(events).contains(EditAboutEvent.ClearClicked)
  }

  @Test
  fun whenIClickAPreset_thenIExpectPresetClickedEvent() {
    setContent(EditAboutState())

    composeTestRule.onNodeWithTag(EditAboutTestTags.LIST).performScrollToNode(hasTestTag(EditAboutTestTags.preset(2)))
    composeTestRule.onNodeWithTag(EditAboutTestTags.preset(2)).performClick()

    assertThat(events).contains(EditAboutEvent.PresetClicked(emoji = "🙏", about = "Be kind"))
  }

  @Test
  fun whenIClickSave_thenIExpectSaveClickedEvent() {
    setContent(EditAboutState(about = "Speak freely"))

    composeTestRule.onNodeWithTag(EditAboutTestTags.BUTTON_SAVE).performClick()

    assertThat(events).contains(EditAboutEvent.SaveClicked)
  }

  @Test
  fun givenAShortAbout_whenIDisplayScreen_thenIExpectNoCount() {
    setContent(EditAboutState(about = "Speak freely"))

    composeTestRule.onNodeWithTag(EditAboutTestTags.COUNT).assertDoesNotExist()
  }

  @Test
  fun givenAnAboutNearTheLimit_whenIDisplayScreen_thenIExpectTheCount() {
    setContent(EditAboutState(about = "a".repeat(EditAboutState.ABOUT_COUNT_DISPLAY_THRESHOLD)))

    composeTestRule.onNodeWithTag(EditAboutTestTags.COUNT).assertExists()
  }

  @Test
  fun givenASavingState_whenIDisplayScreen_thenIExpectSaveAndFieldDisabled() {
    setContent(EditAboutState(about = "Speak freely", saving = true))

    composeTestRule.onNodeWithTag(EditAboutTestTags.BUTTON_SAVE).assertIsNotEnabled()
    input().assertIsNotEnabled()
  }

  /** The editable node inside the about field; the tag sits on the field's decoration around it. */
  private fun input(): SemanticsNodeInteraction {
    return composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and hasAnyAncestor(hasTestTag(EditAboutTestTags.ABOUT_INPUT)))
  }

  private fun setContent(state: EditAboutState) {
    composeTestRule.setContent {
      EditAboutScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }
}
