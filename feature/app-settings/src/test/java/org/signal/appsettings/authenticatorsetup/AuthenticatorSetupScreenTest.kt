/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.authenticatorsetup

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AuthenticatorSetupScreenTest {

  companion object {
    private const val SETUP_KEY = "KVZ7WL3FDDWJZMTOB7PLZPKVRFD4LYSX"
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<AuthenticatorSetupEvent>()

  @Test
  fun whenIClickOpen_thenIExpectOpenAuthenticatorAppEvent() {
    setContent()

    scrollTo(AuthenticatorSetupTestTags.BUTTON_OPEN)

    composeTestRule.onNodeWithTag(AuthenticatorSetupTestTags.BUTTON_OPEN).performClick()

    assertThat(events).contains(AuthenticatorSetupEvent.OpenAuthenticatorAppClicked)
  }

  @Test
  fun whenIClickCopy_thenIExpectCopyKeyEvent() {
    setContent()

    scrollTo(AuthenticatorSetupTestTags.BUTTON_COPY)

    composeTestRule.onNodeWithTag(AuthenticatorSetupTestTags.BUTTON_COPY).performClick()

    assertThat(events).contains(AuthenticatorSetupEvent.CopyKeyClicked)
  }

  @Test
  fun whenIClickContinue_thenIExpectContinueEvent() {
    setContent()

    composeTestRule.onNodeWithTag(AuthenticatorSetupTestTags.BUTTON_CONTINUE)
      .assertIsDisplayed()
      .performClick()

    assertThat(events).contains(AuthenticatorSetupEvent.ContinueClicked)
  }

  private fun setContent() {
    composeTestRule.setContent {
      AuthenticatorSetupScreen(
        state = AuthenticatorSetupState(setupKey = SETUP_KEY),
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(AuthenticatorSetupTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }
}
