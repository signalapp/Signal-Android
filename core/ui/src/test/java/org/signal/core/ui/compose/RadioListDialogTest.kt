/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * Checks when each radio list dialog reports a choice: the plain one as soon as an option is tapped, the confirmation
 * one only once the user says so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RadioListDialogTest {

  companion object {
    private val LABELS = arrayOf("A", "B", "C")
    private val VALUES = arrayOf("a", "b", "c")
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val selections = mutableListOf<Int>()
  private var dismissRequests = 0

  @Test
  fun `the plain dialog reports a tapped option immediately`() {
    setContent {
      Dialogs.RadioListDialog(
        onDismissRequest = { dismissRequests++ },
        title = "Title",
        labels = LABELS,
        values = VALUES,
        selectedIndex = 0,
        onSelected = { selections += it }
      )
    }

    clickOption(2)

    assertThat(selections).containsExactly(2)
    assertThat(dismissRequests).isEqualTo(1)
  }

  @Test
  fun `the confirmation dialog reports nothing until it is confirmed`() {
    setConfirmationContent(selectedIndex = 0)

    clickOption(1)
    clickOption(2)

    assertThat(selections).isEmpty()
    assertThat(dismissRequests).isEqualTo(0)
  }

  @Test
  fun `confirming reports the option that was tapped last`() {
    setConfirmationContent(selectedIndex = 0)

    clickOption(1)
    clickOption(2)
    confirm()

    assertThat(selections).containsExactly(2)
    assertThat(dismissRequests).isEqualTo(1)
  }

  @Test
  fun `confirming without tapping anything reports the option that was already selected`() {
    setConfirmationContent(selectedIndex = 1)

    confirm()

    assertThat(selections).containsExactly(1)
  }

  @Test
  fun `dismissing the confirmation dialog leaves the selection alone`() {
    setConfirmationContent(selectedIndex = 0)

    clickOption(2)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_RADIO_LIST_DIALOG_DISMISS_BUTTON).performClick()

    assertThat(selections).isEmpty()
    assertThat(dismissRequests).isEqualTo(1)
  }

  @Test
  fun `a pick in the confirmation dialog survives the caller's selection changing underneath it`() {
    var callerIndex by mutableStateOf(0)

    setContent {
      Dialogs.RadioListConfirmationDialog(
        onDismissRequest = { dismissRequests++ },
        title = "Title",
        labels = LABELS,
        values = VALUES,
        selectedIndex = callerIndex,
        onConfirm = { selections += it }
      )
    }

    clickOption(2)
    callerIndex = 1
    composeTestRule.waitForIdle()
    confirm()

    assertThat(selections).containsExactly(2)
  }

  @Test
  fun `a confirmation dialog without a valid selection cannot be confirmed until one is made`() {
    setConfirmationContent(selectedIndex = -1)

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_RADIO_LIST_DIALOG_CONFIRM_BUTTON).assertIsNotEnabled()

    clickOption(1)

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_RADIO_LIST_DIALOG_CONFIRM_BUTTON).assertIsEnabled()
    confirm()

    assertThat(selections).containsExactly(1)
  }

  private fun setConfirmationContent(selectedIndex: Int) {
    setContent {
      Dialogs.RadioListConfirmationDialog(
        onDismissRequest = { dismissRequests++ },
        title = "Title",
        labels = LABELS,
        values = VALUES,
        selectedIndex = selectedIndex,
        onConfirm = { selections += it }
      )
    }
  }

  private fun setContent(content: @Composable () -> Unit) {
    composeTestRule.setContent {
      SignalTheme {
        content()
      }
    }
  }

  private fun clickOption(index: Int) {
    composeTestRule.onNodeWithTag(Dialogs.testTagRadioListDialogOption(index)).performClick()
  }

  private fun confirm() {
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_RADIO_LIST_DIALOG_CONFIRM_BUTTON).performClick()
  }
}
