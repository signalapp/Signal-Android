/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.totpsetup

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TotpSetupScreenTest {

  companion object {
    private const val SETUP_KEY = "KVZ7WL3FDDWJZMTOB7PLZPKVRFD4LYSX"
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<TotpSetupEvent>()

  @Test
  fun whenIClickOpen_thenIExpectOpenTotpAppEvent() {
    setContent()

    scrollTo(TotpSetupTestTags.BUTTON_OPEN)

    composeTestRule.onNodeWithTag(TotpSetupTestTags.BUTTON_OPEN).performClick()

    assertThat(events).contains(TotpSetupEvent.OpenTotpAppClicked)
  }

  @Test
  fun whenIClickCopy_thenIExpectCopyKeyEvent() {
    setContent()

    scrollTo(TotpSetupTestTags.BUTTON_COPY)

    composeTestRule.onNodeWithTag(TotpSetupTestTags.BUTTON_COPY).performClick()

    assertThat(events).contains(TotpSetupEvent.CopyKeyClicked)
  }

  @Test
  fun whenIClickContinue_thenIExpectContinueEvent() {
    setContent()

    composeTestRule.onNodeWithTag(TotpSetupTestTags.BUTTON_CONTINUE)
      .assertIsDisplayed()
      .performClick()

    assertThat(events).contains(TotpSetupEvent.ContinueClicked)
  }

  @Test
  fun whenTheKeyHasntArrived_thenIExpectASpinnerAndNothingToClick() {
    setContent(TotpSetupState(loading = true))

    scrollTo(TotpSetupTestTags.SETUP_KEY_SPINNER)

    composeTestRule.onNodeWithTag(TotpSetupTestTags.SETUP_KEY_SPINNER).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TotpSetupTestTags.BUTTON_COPY).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(TotpSetupTestTags.BUTTON_CONTINUE).assertIsNotEnabled()

    composeTestRule.onNodeWithTag(TotpSetupTestTags.BUTTON_CONTINUE).performClick()

    assertThat(events).isEmpty()
  }

  @Test
  fun whenTheKeyHasArrived_thenIExpectItInPlaceOfTheSpinner() {
    setContent()

    scrollTo(TotpSetupTestTags.SETUP_KEY)

    composeTestRule.onNodeWithTag(TotpSetupTestTags.SETUP_KEY).assertIsDisplayed()
  }

  @Test
  fun whenIDismissAFailureDialog_thenIExpectDialogDismissedEvent() {
    setContent(TotpSetupState(loading = false, dialog = TotpSetupState.Dialog.NetworkFailure))

    composeTestRule.onNodeWithText("OK").performClick()

    assertThat(events).contains(TotpSetupEvent.DialogDismissed)
  }

  private fun setContent(state: TotpSetupState = TotpSetupState(setupKey = SETUP_KEY, loading = false)) {
    composeTestRule.setContent {
      TotpSetupScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(TotpSetupTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }
}
