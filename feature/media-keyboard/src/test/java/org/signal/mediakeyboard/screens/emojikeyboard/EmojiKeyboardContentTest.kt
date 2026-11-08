/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emojikeyboard

import android.app.Application
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediakeyboard.data.KeyboardEmoji
import org.signal.mediakeyboard.screens.emoji.EmojiPageScreenEvents
import org.signal.mediakeyboard.screens.emoji.previewEmojiPageState

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EmojiKeyboardContentTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<EmojiKeyboardEvents>()
  private val page = previewEmojiPageState()

  @Test
  fun `clicking backspace emits BackspaceClicked`() {
    setContent(EmojiKeyboardState(page = page))

    composeTestRule.onNodeWithTag(EmojiKeyboardTestTags.BACKSPACE).performClick()

    assertThat(events).contains(EmojiKeyboardEvents.BackspaceClicked)
  }

  @Test
  fun `clicking search emits SearchClicked`() {
    setContent(EmojiKeyboardState(page = page))

    composeTestRule.onNodeWithTag(EmojiKeyboardTestTags.SEARCH).performClick()

    assertThat(events).contains(EmojiKeyboardEvents.SearchClicked)
  }

  @Test
  fun `searching focuses the search field`() {
    setContent(EmojiKeyboardState(page = page, isSearching = true))

    composeTestRule.onNodeWithTag(EmojiKeyboardTestTags.SEARCH_FIELD).assertIsFocused()
  }

  @Test
  fun `closing search emits SearchCloseClicked`() {
    setContent(EmojiKeyboardState(page = page, isSearching = true))

    composeTestRule.onNodeWithTag(EmojiKeyboardTestTags.CLOSE_SEARCH).performClick()

    assertThat(events).contains(EmojiKeyboardEvents.SearchCloseClicked)
  }

  @Test
  fun `search results show in a row and can be picked`() {
    setContent(EmojiKeyboardState(page = page.copy(searchResults = listOf(KeyboardEmoji("🍔")), searchQuery = "burger"), isSearching = true))

    composeTestRule.onNodeWithText("🍔").assertIsDisplayed().performClick()

    assertThat(events).contains(EmojiKeyboardEvents.EmojiPageScreenEvent(EmojiPageScreenEvents.EmojiClicked(KeyboardEmoji("🍔"))))
  }

  @Test
  fun `an empty search offers recents`() {
    setContent(EmojiKeyboardState(page = page, isSearching = true))

    composeTestRule.onNodeWithText("🎉").assertIsDisplayed()
  }

  private fun setContent(state: EmojiKeyboardState) {
    composeTestRule.setContent {
      SignalTheme {
        EmojiKeyboardContent(
          state = state,
          searchFieldState = rememberTextFieldState(),
          onEvent = { events += it }
        )
      }
    }
  }
}
