/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.totpcodeentry

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.uicomponents.codeentryfield.CodeEntryFieldEvents
import org.signal.uicomponents.codeentryfield.CodeEntryFieldState
import org.signal.uicomponents.codeentryfield.CodeEntryFieldTestTags

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TotpCodeEntryScreenTest {

  companion object {
    private val FULL_CODE = CodeEntryFieldState(digits = listOf("1", "2", "3", "4", "5", "6"))
    private val PARTIAL_CODE = CodeEntryFieldState(digits = listOf("1", "2", "3", "", "", ""))
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<TotpCodeEntryEvent>()

  @Test
  fun givenAPartialCode_whenScreenDisplayed_thenNextIsDisabled() {
    setContent(TotpCodeEntryState(codeEntry = PARTIAL_CODE))

    composeTestRule.onNodeWithTag(TotpCodeEntryTestTags.BUTTON_NEXT).assertIsNotEnabled()
  }

  @Test
  fun givenAFullCode_whenIClickNext_thenIExpectNextEvent() {
    setContent(TotpCodeEntryState(codeEntry = FULL_CODE))

    composeTestRule.onNodeWithTag(TotpCodeEntryTestTags.BUTTON_NEXT)
      .assertIsEnabled()
      .performClick()

    assertThat(events).contains(TotpCodeEntryEvent.NextClicked)
  }

  @Test
  fun whenITypeInTheCodeField_thenIExpectAForwardedCodeEntryEvent() {
    setContent(TotpCodeEntryState())

    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.digit(0)).performTextInput("1")
    composeTestRule.waitForIdle()

    assertThat(events).contains(TotpCodeEntryEvent.CodeEntryEvent(CodeEntryFieldEvents.DigitChanged(0, "1")))
  }

  @Test
  fun givenAnIncorrectCode_whenScreenDisplayed_thenIExpectAnError() {
    setContent(TotpCodeEntryState(codeEntry = FULL_CODE, error = TotpCodeEntryState.Error.IncorrectCode))

    composeTestRule.onNodeWithTag(TotpCodeEntryTestTags.ERROR).assertIsDisplayed()
  }

  @Test
  fun givenASubmissionInFlight_whenScreenDisplayed_thenTheCodeFieldIsDisabled() {
    setContent(TotpCodeEntryState(codeEntry = FULL_CODE, submitting = true))

    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.digit(0)).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(TotpCodeEntryTestTags.BUTTON_NEXT).assertIsNotEnabled()
  }

  private fun setContent(state: TotpCodeEntryState) {
    composeTestRule.setContent {
      TotpCodeEntryScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }
}
