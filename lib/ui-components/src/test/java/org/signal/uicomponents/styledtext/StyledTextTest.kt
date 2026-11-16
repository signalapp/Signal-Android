/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.styledtext

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.emoji.EmojiDependencies

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StyledTextTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Before
  fun setUp() {
    EmojiDependencies.init(
      ApplicationProvider.getApplicationContext(),
      mockk(relaxed = true) {
        every { providePreferSystemEmoji() } returns true
      }
    )
  }

  @Test
  fun `the suffix is appended after the text`() {
    setContent(StyledString("caption"), suffix = AnnotatedString(" more"))

    composeTestRule.onNodeWithTag(TAG).assertTextEquals("caption more")
  }

  @Test
  fun `a hidden spoiler is masked until tapped`() {
    composeTestRule.mainClock.autoAdvance = false
    setContent(StyledString("secret", styles = listOf(StyleRange(0, 6, StyleRange.Style.SPOILER))))
    composeTestRule.mainClock.advanceTimeByFrame()

    composeTestRule.onNodeWithText("secret").assertDoesNotExist()

    composeTestRule.onNodeWithTag(TAG).performTouchInput { click(Offset(0.5f, centerY)) }
    composeTestRule.mainClock.advanceTimeByFrame()

    composeTestRule.onNodeWithText("secret").assertExists()
  }

  @Test
  fun `the layout reports offsets in raw text indices`() {
    var lastLineEnd = -1
    setContent(StyledString("one\ntwo")) { layout ->
      lastLineEnd = layout.toTextIndex(layout.textLayoutResult.getLineEnd(1))
    }

    composeTestRule.waitForIdle()
    assertThat(lastLineEnd).isEqualTo(7)
  }

  private fun setContent(
    text: StyledString,
    suffix: AnnotatedString? = null,
    onTextLayout: (StyledTextLayout) -> Unit = {}
  ) {
    composeTestRule.setContent {
      StyledText(
        text = text,
        suffix = suffix,
        onTextLayout = onTextLayout,
        modifier = Modifier.testTag(TAG)
      )
    }
  }

  companion object {
    private const val TAG = "styled-text"
  }
}
