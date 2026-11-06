/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.advancedpin

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.compose.Dialogs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AdvancedPinSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<AdvancedPinSettingsEvent>()
  private val interactionEvents: List<AdvancedPinSettingsEvent>
    get() = events.filterNot { it == AdvancedPinSettingsEvent.ScreenResumed }

  @Test
  fun `showing the screen emits ScreenResumed and nothing else`() {
    setContent(AdvancedPinSettingsState())

    assertThat(events).containsExactly(AdvancedPinSettingsEvent.ScreenResumed)
  }

  @Test
  fun `with the PIN on the row offers to disable it`() {
    setContent(AdvancedPinSettingsState(hasOptedOutOfPin = false))

    composeTestRule.onNodeWithText("Disable PIN").assertIsDisplayed()
  }

  @Test
  fun `with the PIN off the row offers to enable it`() {
    setContent(AdvancedPinSettingsState(hasOptedOutOfPin = true))

    composeTestRule.onNodeWithText("Enable PIN").assertIsDisplayed()
  }

  @Test
  fun `clicking the row emits PinToggleClicked`() {
    setContent(AdvancedPinSettingsState())

    composeTestRule.onNodeWithTag(AdvancedPinSettingsTestTags.ROW_PIN_TOGGLE).performClick()

    assertThat(interactionEvents).containsExactly(AdvancedPinSettingsEvent.PinToggleClicked)
  }

  @Test
  fun `confirming the rotate key dialog emits RotateBackupKeyConfirmed`() {
    setContent(AdvancedPinSettingsState(dialog = AdvancedPinSettingsState.Dialog.RotateBackupKey))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(interactionEvents).containsExactly(AdvancedPinSettingsEvent.RotateBackupKeyConfirmed)
  }

  @Test
  fun `confirming the recovery phrase dialog emits RecordRecoveryPhraseConfirmed`() {
    setContent(AdvancedPinSettingsState(dialog = AdvancedPinSettingsState.Dialog.RecordPaymentsRecoveryPhrase))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(interactionEvents).containsExactly(AdvancedPinSettingsEvent.RecordRecoveryPhraseConfirmed)
  }

  @Test
  fun `cancelling a dialog emits DialogDismissed`() {
    setContent(AdvancedPinSettingsState(dialog = AdvancedPinSettingsState.Dialog.RotateBackupKey))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON).performClick()

    assertThat(interactionEvents).containsExactly(AdvancedPinSettingsEvent.DialogDismissed)
  }

  @Test
  fun `the registration lock dialog shows and dismisses`() {
    setContent(AdvancedPinSettingsState(dialog = AdvancedPinSettingsState.Dialog.RegistrationLockRequiresPin))

    composeTestRule.onNodeWithText("OK").performClick()

    assertThat(interactionEvents).containsExactly(AdvancedPinSettingsEvent.DialogDismissed)
  }

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    setContent(AdvancedPinSettingsState())

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(interactionEvents).containsExactly(AdvancedPinSettingsEvent.NavigateBackClicked)
  }

  private fun setContent(state: AdvancedPinSettingsState) {
    composeTestRule.setContent {
      AdvancedPinSettingsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }
}
