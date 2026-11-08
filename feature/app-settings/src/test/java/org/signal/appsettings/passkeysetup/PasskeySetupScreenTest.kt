/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.passkeysetup

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.compose.Dialogs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PasskeySetupScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<PasskeySetupEvent>()

  @Test
  fun `tapping set up a passkey sends SetUpPasskeyClicked`() {
    setContent()

    composeTestRule.onNodeWithTag(PasskeySetupTestTags.BUTTON_SET_UP)
      .assertIsDisplayed()
      .performClick()

    assertThat(events).contains(PasskeySetupEvent.SetUpPasskeyClicked)
  }

  @Test
  fun `tapping learn more sends LearnMoreClicked`() {
    setContent()

    scrollTo(PasskeySetupTestTags.LINK_LEARN_MORE)
    composeTestRule.onNodeWithTag(PasskeySetupTestTags.LINK_LEARN_MORE)
      .onChildren()
      .onFirst()
      .performSemanticsAction(SemanticsActions.OnClick)

    assertThat(events).contains(PasskeySetupEvent.LearnMoreClicked)
  }

  @Test
  fun `tapping ok on the max two factor methods dialog sends DialogDismissed`() {
    setContent(PasskeySetupState(dialog = PasskeySetupState.Dialog.MaxTwoFactorMethodsReached(maxTwoFactorMethods = 10)))

    composeTestRule.onNodeWithTag(PasskeySetupTestTags.DIALOG_MAX_TWO_FACTOR_METHODS_REACHED).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(events).contains(PasskeySetupEvent.DialogDismissed)
  }

  @Test
  fun `tapping learn more on the max two factor methods dialog sends LearnMoreClicked and DialogDismissed`() {
    setContent(PasskeySetupState(dialog = PasskeySetupState.Dialog.MaxTwoFactorMethodsReached(maxTwoFactorMethods = 10)))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON).performClick()

    assertThat(events).contains(PasskeySetupEvent.LearnMoreClicked)
    assertThat(events).contains(PasskeySetupEvent.DialogDismissed)
  }

  private fun setContent(state: PasskeySetupState = PasskeySetupState()) {
    composeTestRule.setContent {
      PasskeySetupScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(PasskeySetupTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }
}
