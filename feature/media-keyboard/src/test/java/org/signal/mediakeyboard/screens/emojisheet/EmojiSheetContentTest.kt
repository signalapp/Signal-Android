/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emojisheet

import android.app.Application
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.BottomSheetHostState
import org.signal.core.ui.compose.LocalBottomSheetHostState
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediakeyboard.data.KeyboardEmoji
import org.signal.mediakeyboard.screens.emoji.EmojiPageScreenEvents

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EmojiSheetContentTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<EmojiSheetEvents>()

  @Test
  fun `clicking an emoji emits EmojiClicked`() {
    setContent(previewEmojiSheetState())

    composeTestRule.onNodeWithText("❤️").performClick()

    assertThat(events).contains(EmojiSheetEvents.EmojiPageScreenEvent(EmojiPageScreenEvents.EmojiClicked(KeyboardEmoji("❤️"))))
  }

  @Test
  fun `clicking customize reactions emits CustomizeReactionsClicked`() {
    setContent(previewEmojiSheetState(showCustomizeReactions = true))

    composeTestRule.onNodeWithTag(EmojiSheetTestTags.CUSTOMIZE_REACTIONS).performClick()

    assertThat(events).contains(EmojiSheetEvents.CustomizeReactionsClicked)
  }

  @Test
  fun `customize reactions is hidden unless asked for`() {
    setContent(previewEmojiSheetState(showCustomizeReactions = false))

    composeTestRule.onNodeWithTag(EmojiSheetTestTags.CUSTOMIZE_REACTIONS).assertDoesNotExist()
  }

  @Test
  fun `focusing the search field expands the sheet`() {
    var expandRequests = 0
    setContent(previewEmojiSheetState(), sheet = BottomSheetHostState(onExpand = { expandRequests++ }))

    composeTestRule.onNodeWithTag(EmojiSheetTestTags.SEARCH_FIELD).performClick()

    assertThat(expandRequests).isEqualTo(1)
  }

  @Test
  fun `search results replace the grid`() {
    val state = previewEmojiSheetState()
    setContent(state.copy(page = state.page.copy(searchResults = listOf(KeyboardEmoji("🍔")), searchQuery = "burger")))

    composeTestRule.onNodeWithText("🍔").assertIsDisplayed()
    composeTestRule.onNodeWithText("❤️").assertDoesNotExist()
  }

  private fun setContent(state: EmojiSheetState, sheet: BottomSheetHostState = BottomSheetHostState()) {
    composeTestRule.setContent {
      SignalTheme {
        CompositionLocalProvider(LocalBottomSheetHostState provides sheet) {
          EmojiSheetContent(
            state = state,
            searchFieldState = rememberTextFieldState(),
            onEvent = { events += it }
          )
        }
      }
    }
  }
}
