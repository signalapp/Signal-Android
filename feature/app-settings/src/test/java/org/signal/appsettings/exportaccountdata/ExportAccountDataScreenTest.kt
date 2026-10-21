/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.exportaccountdata

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.exportaccountdata.ExportAccountDataState.Dialog
import org.signal.core.ui.compose.Dialogs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExportAccountDataScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<ExportAccountDataEvent>()

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    setContent(ExportAccountDataState())

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(events).containsExactly(ExportAccountDataEvent.NavigateBackClicked)
  }

  @Test
  fun `clicking the JSON row emits FormatSelected(JSON)`() {
    setContent(ExportAccountDataState(format = ExportAccountDataFormat.TXT))

    scrollTo(ExportAccountDataTestTags.ROW_FORMAT_JSON)
    composeTestRule.onNodeWithTag(ExportAccountDataTestTags.ROW_FORMAT_JSON).performClick()

    assertThat(events).containsExactly(ExportAccountDataEvent.FormatSelected(ExportAccountDataFormat.JSON))
  }

  @Test
  fun `clicking the TXT row emits FormatSelected(TXT)`() {
    setContent(ExportAccountDataState(format = ExportAccountDataFormat.JSON))

    scrollTo(ExportAccountDataTestTags.ROW_FORMAT_TXT)
    composeTestRule.onNodeWithTag(ExportAccountDataTestTags.ROW_FORMAT_TXT).performClick()

    assertThat(events).containsExactly(ExportAccountDataEvent.FormatSelected(ExportAccountDataFormat.TXT))
  }

  @Test
  fun `clicking export emits ExportReportClicked`() {
    setContent(ExportAccountDataState())

    scrollTo(ExportAccountDataTestTags.BUTTON_EXPORT_REPORT)
    composeTestRule.onNodeWithTag(ExportAccountDataTestTags.BUTTON_EXPORT_REPORT).performClick()

    assertThat(events).containsExactly(ExportAccountDataEvent.ExportReportClicked)
  }

  @Test
  fun `confirming the export emits only ExportConfirmed`() {
    setContent(ExportAccountDataState(dialog = Dialog.ConfirmExport))

    composeTestRule.onNodeWithTag(ExportAccountDataTestTags.DIALOG_CONFIRM_EXPORT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(events).containsExactly(ExportAccountDataEvent.ExportConfirmed)
  }

  @Test
  fun `canceling the export emits DialogDismissed`() {
    setContent(ExportAccountDataState(dialog = Dialog.ConfirmExport))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON).performClick()

    assertThat(events).containsExactly(ExportAccountDataEvent.DialogDismissed)
  }

  @Test
  fun `generating the report shows progress`() {
    setContent(ExportAccountDataState(dialog = Dialog.GeneratingReport))

    composeTestRule.onNodeWithTag(ExportAccountDataTestTags.DIALOG_GENERATING_REPORT).assertIsDisplayed()
    composeTestRule.onNodeWithText("Generating report…").assertIsDisplayed()
  }

  @Test
  fun `acknowledging the failure emits DialogDismissed`() {
    setContent(ExportAccountDataState(dialog = Dialog.ReportGenerationFailed))

    composeTestRule.onNodeWithTag(ExportAccountDataTestTags.DIALOG_REPORT_GENERATION_FAILED).assertIsDisplayed()
    composeTestRule.onNodeWithText("OK").performClick()

    assertThat(events).containsExactly(ExportAccountDataEvent.DialogDismissed)
  }

  private fun setContent(state: ExportAccountDataState) {
    composeTestRule.setContent {
      ExportAccountDataScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(ExportAccountDataTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }
}
