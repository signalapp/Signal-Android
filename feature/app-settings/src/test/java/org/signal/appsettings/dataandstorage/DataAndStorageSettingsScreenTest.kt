/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.dataandstorage

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.calls.CallDataMode
import org.signal.core.models.media.SentMediaQuality

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DataAndStorageSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<DataAndStorageSettingsEvent>()

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    setContent(DataAndStorageSettingsState())

    composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

    assertThat(events).containsExactly(DataAndStorageSettingsEvent.NavigateBackClicked)
  }

  @Test
  fun `storage use shows calculating until it is known`() {
    setContent(DataAndStorageSettingsState(totalStorageUseBytes = null))

    composeTestRule.onNodeWithText("Calculating…", substring = true).assertIsDisplayed()
  }

  @Test
  fun `clicking manage storage emits ManageStorageClicked`() {
    setContent(DataAndStorageSettingsState())

    composeTestRule.onNodeWithTag(DataAndStorageSettingsTestTags.ROW_MANAGE_STORAGE).performClick()

    assertThat(events).containsExactly(DataAndStorageSettingsEvent.ManageStorageClicked)
  }

  /** The multi-select dialog caps its height at the screen height minus 200dp, which pushes its buttons offscreen on Robolectric's default screen. */
  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `picking an auto download type emits AutoDownloadChanged for that network`() {
    setContent(DataAndStorageSettingsState(autoDownload = mapOf(AutoDownloadNetwork.WIFI to setOf(AutoDownloadMediaType.IMAGES))))

    val tag = DataAndStorageSettingsTestTags.ROW_AUTO_DOWNLOAD_PREFIX + AutoDownloadNetwork.WIFI.name
    scrollTo(tag)
    composeTestRule.onNodeWithTag(tag).performClick()
    composeTestRule.onNodeWithText("Video").performClick()
    composeTestRule.onNodeWithText("OK").performClick()

    assertThat(events).containsExactly(
      DataAndStorageSettingsEvent.AutoDownloadChanged(AutoDownloadNetwork.WIFI, setOf(AutoDownloadMediaType.IMAGES, AutoDownloadMediaType.VIDEO))
    )
  }

  @Test
  fun `picking a sent media quality emits SentMediaQualitySelected`() {
    setContent(DataAndStorageSettingsState(sentMediaQuality = SentMediaQuality.STANDARD))

    scrollTo(DataAndStorageSettingsTestTags.ROW_SENT_MEDIA_QUALITY)
    composeTestRule.onNodeWithTag(DataAndStorageSettingsTestTags.ROW_SENT_MEDIA_QUALITY).performClick()
    composeTestRule.onNodeWithText("High").performClick()

    assertThat(events).contains(DataAndStorageSettingsEvent.SentMediaQualitySelected(SentMediaQuality.HIGH))
  }

  @Test
  fun `picking when to use less data for calls emits CallDataModeSelected`() {
    setContent(DataAndStorageSettingsState(callDataMode = CallDataMode.HIGH_ALWAYS))

    scrollTo(DataAndStorageSettingsTestTags.ROW_CALL_DATA_MODE)
    composeTestRule.onNodeWithTag(DataAndStorageSettingsTestTags.ROW_CALL_DATA_MODE).performClick()
    composeTestRule.onNodeWithText("WiFi and mobile data").performClick()

    assertThat(events).contains(DataAndStorageSettingsEvent.CallDataModeSelected(CallDataMode.LOW_ALWAYS))
  }

  @Test
  fun `toggling stay connected in background emits StayConnectedInBackgroundToggled`() {
    setContent(DataAndStorageSettingsState(forceWebsocketMode = false))

    scrollTo(DataAndStorageSettingsTestTags.ROW_STAY_CONNECTED)
    composeTestRule.onNodeWithTag(DataAndStorageSettingsTestTags.ROW_STAY_CONNECTED)
      .assertIsEnabled()
      .performClick()

    assertThat(events).containsExactly(DataAndStorageSettingsEvent.StayConnectedInBackgroundToggled(true))
  }

  @Test
  fun `stay connected in background is locked on without play services`() {
    setContent(DataAndStorageSettingsState(forceWebsocketMode = false, playServicesAvailable = false))

    scrollTo(DataAndStorageSettingsTestTags.ROW_STAY_CONNECTED)
    composeTestRule.onNodeWithTag(DataAndStorageSettingsTestTags.ROW_STAY_CONNECTED).assertIsNotEnabled()
    composeTestRule.onNode(isToggleable() and hasAnyAncestor(hasTestTag(DataAndStorageSettingsTestTags.ROW_STAY_CONNECTED)), useUnmergedTree = true).assertIsOn()
  }

  @Test
  fun `clicking use proxy emits UseProxyClicked`() {
    setContent(DataAndStorageSettingsState())

    scrollTo(DataAndStorageSettingsTestTags.ROW_USE_PROXY)
    composeTestRule.onNodeWithTag(DataAndStorageSettingsTestTags.ROW_USE_PROXY).performClick()

    assertThat(events).containsExactly(DataAndStorageSettingsEvent.UseProxyClicked)
  }

  @Test
  fun `the confirmation dialog emits confirm and dismiss events`() {
    setContent(DataAndStorageSettingsState(dialog = DataAndStorageSettingsState.Dialog.ConfirmStayConnectedInBackground))

    composeTestRule.onNodeWithText("Enable").performClick()
    composeTestRule.onNodeWithText("Cancel").performClick()

    assertThat(events).containsExactly(
      DataAndStorageSettingsEvent.StayConnectedInBackgroundConfirmed,
      DataAndStorageSettingsEvent.DialogDismissed
    )
  }

  private fun setContent(state: DataAndStorageSettingsState) {
    composeTestRule.setContent {
      DataAndStorageSettingsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(DataAndStorageSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }
}
