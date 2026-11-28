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
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
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

  private val codeChanges: List<String>
    get() = events.filterIsInstance<CodeEntryFieldEvents.CodeChanged>().map { it.code }

  @Test
  fun `field displays one box per digit`() {
    setContent(CodeEntryFieldState())

    for (index in 0 until CODE_LENGTH) {
      digit(index).assertIsDisplayed()
    }
  }

  @Test
  fun `field renders the initial code from state`() {
    setContent(CodeEntryFieldState(code = "418"))

    digit(0).assertTextEquals("4")
    digit(2).assertTextEquals("8")
    digit(3).assertTextEquals("")
  }

  @Test
  fun `typing emits the whole code after each keystroke`() {
    setContent(CodeEntryFieldState())

    field().performTextInput("4")
    composeTestRule.waitForIdle()
    field().performTextInput("1")
    composeTestRule.waitForIdle()

    assertThat(codeChanges).contains("4")
    assertThat(codeChanges.last()).isEqualTo("41")
  }

  @Test
  fun `rapid keystrokes are all kept even when the state never catches up`() {
    setContent(CodeEntryFieldState())

    "418372".forEach { field().performTextInput(it.toString()) }
    composeTestRule.waitForIdle()

    assertThat(codeChanges.last()).isEqualTo("418372")
    digit(0).assertTextEquals("4")
    digit(5).assertTextEquals("2")
  }

  @Test
  fun `pasting a hyphenated code keeps only the digits`() {
    setContent(CodeEntryFieldState())

    field().performTextInput("418-372")
    composeTestRule.waitForIdle()

    assertThat(codeChanges.last()).isEqualTo("418372")
  }

  @Test
  fun `pasting a full code over a partial code replaces it`() {
    setContent(CodeEntryFieldState(code = "12"))

    field().performTextInput("418372")
    composeTestRule.waitForIdle()

    assertThat(codeChanges.last()).isEqualTo("418372")
  }

  @Test
  fun `typing past a full code is ignored`() {
    setContent(CodeEntryFieldState(code = "418372"))

    field().performTextInput("7")
    composeTestRule.waitForIdle()

    assertThat(codeChanges).containsOnly("418372")
  }

  @Test
  fun `a pending overwrite replaces the field contents and is acknowledged`() {
    setContent(CodeEntryFieldState(code = "12", pendingOverwrite = "418372"))

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      events.any { it is CodeEntryFieldEvents.OverwriteApplied }
    }
    composeTestRule.waitForIdle()

    assertThat(codeChanges.last()).isEqualTo("418372")
    digit(5).assertTextEquals("2")
  }

  @Test
  fun `a disabled field cannot be typed in`() {
    setContent(CodeEntryFieldState(), enabled = false)

    field().assertIsNotEnabled()
  }

  private fun field() = composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.ROOT)

  private fun digit(index: Int) = composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.digit(index), useUnmergedTree = true)

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
