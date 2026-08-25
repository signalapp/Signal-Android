/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.authenticatorcodeentry

import android.app.Application
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AuthenticatorCodeEntryScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<AuthenticatorCodeEntryEvent>()

  @Test
  fun givenAPartialCode_whenScreenDisplayed_thenDoneIsDisabled() {
    setContent(AuthenticatorCodeEntryState(code = "123"))

    composeTestRule.onNodeWithTag(AuthenticatorCodeEntryTestTags.BUTTON_DONE).assertIsNotEnabled()
  }

  @Test
  fun givenAFullCode_whenIClickDone_thenIExpectDoneEvent() {
    setContent(AuthenticatorCodeEntryState(code = "123456"))

    composeTestRule.onNodeWithTag(AuthenticatorCodeEntryTestTags.BUTTON_DONE)
      .assertIsEnabled()
      .performClick()

    assertThat(events).contains(AuthenticatorCodeEntryEvent.DoneClicked)
  }

  @Test
  fun whenITypeInTheCodeField_thenIExpectCodeChangedEvent() {
    setContent(AuthenticatorCodeEntryState())

    composeTestRule.onNodeWithTag(AuthenticatorCodeEntryTestTags.CODE_INPUT).performTextInput("123456")

    assertThat(events).contains(AuthenticatorCodeEntryEvent.CodeChanged("123456"))
  }

  private fun setContent(state: AuthenticatorCodeEntryState) {
    composeTestRule.setContent {
      AuthenticatorCodeEntryScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }
}
