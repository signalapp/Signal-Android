/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * Checks how [Rows.RadioListRow] hands its choice back, with and without the confirmation step.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RadioListRowTest {

  companion object {
    private const val ROW = "row"

    private val LABELS = arrayOf("A", "B", "C")
    private val VALUES = arrayOf("a", "b", "c")
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val selections = mutableListOf<String>()

  @Test
  fun `a plain row reports the option that was tapped`() {
    setContent(requireConfirmation = false)

    composeTestRule.onNodeWithTag(ROW).performClick()
    clickOption(2)

    assertThat(selections).containsExactly("c")
  }

  @Test
  fun `a confirmation row reports nothing until it is confirmed`() {
    setContent(requireConfirmation = true)

    composeTestRule.onNodeWithTag(ROW).performClick()
    clickOption(2)

    assertThat(selections).isEmpty()
  }

  @Test
  fun `a confirmation row reports the option that was confirmed`() {
    setContent(requireConfirmation = true)

    composeTestRule.onNodeWithTag(ROW).performClick()
    clickOption(2)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_RADIO_LIST_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(selections).containsExactly("c")
  }

  @Test
  fun `confirming closes the dialog`() {
    setContent(requireConfirmation = true)

    composeTestRule.onNodeWithTag(ROW).performClick()
    composeTestRule.onNodeWithTag(Dialogs.testTagRadioListDialogOption(1)).assertIsDisplayed()

    clickOption(1)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_RADIO_LIST_DIALOG_CONFIRM_BUTTON).performClick()

    composeTestRule.onNodeWithTag(Dialogs.testTagRadioListDialogOption(1)).assertDoesNotExist()
  }

  @Test
  fun `a disabled row does not open its dialog`() {
    setContent(requireConfirmation = true, enabled = false)

    composeTestRule.onNodeWithTag(ROW).performClick()

    composeTestRule.onNodeWithTag(Dialogs.testTagRadioListDialogOption(0)).assertDoesNotExist()
  }

  private fun setContent(requireConfirmation: Boolean, enabled: Boolean = true) {
    composeTestRule.setContent {
      SignalTheme {
        Rows.RadioListRow(
          text = "Radio List",
          labels = LABELS,
          values = VALUES,
          selectedValue = "a",
          onSelected = { selections += it },
          modifier = Modifier.testTag(ROW),
          enabled = enabled,
          requireConfirmation = requireConfirmation
        )
      }
    }
  }

  private fun clickOption(index: Int) {
    composeTestRule.onNodeWithTag(Dialogs.testTagRadioListDialogOption(index)).performClick()
  }
}
