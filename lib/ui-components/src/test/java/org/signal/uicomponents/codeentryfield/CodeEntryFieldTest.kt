/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.codeentryfield

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.uicomponents.codeentryfield.CodeEntryFieldState.Companion.CODE_LENGTH

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CodeEntryFieldTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<CodeEntryFieldEvents>()

  @Test
  fun `field displays one box per digit`() {
    setContent(CodeEntryFieldState())

    for (index in 0 until CODE_LENGTH) {
      composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.digit(index)).assertIsDisplayed()
    }
  }

  @Test
  fun `field renders the digits from state`() {
    setContent(CodeEntryFieldState(digits = listOf("4", "1", "8", "3", "7", "2")))

    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.digit(0)).assertTextEquals("4")
    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.digit(5)).assertTextEquals("2")
  }

  @Test
  fun `entering a digit emits DigitChanged for that box`() {
    setContent(CodeEntryFieldState())

    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.digit(0)).performTextInput("4")
    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.digit(1)).performTextInput("1")
    composeTestRule.waitForIdle()

    assertThat(events).contains(CodeEntryFieldEvents.DigitChanged(0, "4"))
    assertThat(events).contains(CodeEntryFieldEvents.DigitChanged(1, "1"))
  }

  @Test
  fun `pasting into a box emits DigitChanged with the raw text`() {
    setContent(CodeEntryFieldState())

    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.digit(0)).performTextInput("418-372")
    composeTestRule.waitForIdle()

    assertThat(events).contains(CodeEntryFieldEvents.DigitChanged(0, "418-372"))
  }

  @Test
  fun `a disabled field cannot be typed in`() {
    setContent(CodeEntryFieldState(), enabled = false)

    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.digit(0)).assertIsNotEnabled()
  }

  private fun setContent(state: CodeEntryFieldState, enabled: Boolean = true) {
    composeTestRule.setContent {
      CodeEntryField(
        state = state,
        onEvent = { events += it },
        enabled = enabled
      )
    }
  }
}
