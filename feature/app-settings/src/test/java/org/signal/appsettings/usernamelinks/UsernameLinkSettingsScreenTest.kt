/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernamelinks

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.QrCodeData

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UsernameLinkSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<UsernameLinkSettingsEvent>()

  /** Everything but the [UsernameLinkSettingsEvent.ScreenResumed] the screen sends as soon as it's shown. */
  private val interactionEvents: List<UsernameLinkSettingsEvent>
    get() = events.filterNot { it == UsernameLinkSettingsEvent.ScreenResumed }

  private val state = UsernameLinkSettingsState(
    username = "parker.42",
    linkState = UsernameLinkSettingsState.LinkState.Present(LINK),
    qrCodeState = QrCodeState.Present(QrCodeData.forData(LINK))
  )

  @Test
  fun whenIDisplayTheScreen_thenIExpectOnlyScreenResumed() {
    setContent(state)

    assertThat(events).containsExactly(UsernameLinkSettingsEvent.ScreenResumed)
  }

  @Test
  fun whenIClickEachCodeTabControl_thenIExpectTheirEvents() {
    setContent(state)

    composeTestRule.onNodeWithTag(UsernameLinkSettingsTestTags.BUTTON_LINK).performClick()
    composeTestRule.onNodeWithTag(UsernameLinkSettingsTestTags.BUTTON_SHARE).performClick()
    composeTestRule.onNodeWithTag(UsernameLinkSettingsTestTags.BUTTON_COLOR).performClick()
    composeTestRule.onNodeWithTag(UsernameLinkSettingsTestTags.BUTTON_RESET).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(UsernameLinkSettingsTestTags.TAB_SCAN).performClick()
    composeTestRule.onNodeWithTag(UsernameLinkSettingsTestTags.BUTTON_CLOSE).performClick()

    assertThat(interactionEvents).containsExactly(
      UsernameLinkSettingsEvent.LinkClicked,
      UsernameLinkSettingsEvent.ShareQrCodeClicked,
      UsernameLinkSettingsEvent.ColorClicked,
      UsernameLinkSettingsEvent.ResetClicked,
      UsernameLinkSettingsEvent.ScanTabSelected,
      UsernameLinkSettingsEvent.NavigateBackClicked
    )
  }

  @Test
  fun givenNoLink_whenIClickTheLinkButton_thenIExpectNothing() {
    setContent(state.copy(linkState = UsernameLinkSettingsState.LinkState.NotSet))

    composeTestRule.onNodeWithTag(UsernameLinkSettingsTestTags.BUTTON_LINK).performClick()

    assertThat(interactionEvents).isEmpty()
  }

  @Test
  fun givenTheScanTabWithoutTheCamera_whenIClickItsButtons_thenIExpectTheirEvents() {
    setContent(state.copy(activeTab = UsernameLinkSettingsState.Tab.SCAN, hasCameraPermission = false))

    composeTestRule.onNodeWithTag(UsernameQrScanTabTestTags.BUTTON_ALLOW_CAMERA).performClick()
    composeTestRule.onNodeWithTag(UsernameQrScanTabTestTags.BUTTON_OPEN_GALLERY).performClick()

    assertThat(interactionEvents).containsExactly(UsernameLinkSettingsEvent.AllowCameraClicked, UsernameLinkSettingsEvent.OpenGalleryClicked)
  }

  @Test
  fun givenTheResetConfirmation_whenIConfirm_thenIExpectResetConfirmed() {
    setContent(state.copy(dialog = UsernameLinkSettingsState.Dialog.ConfirmReset))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(interactionEvents).containsExactly(UsernameLinkSettingsEvent.ResetConfirmed)
  }

  @Test
  fun givenAFailedScan_whenIDisplayTheScreen_thenIExpectWhy() {
    setContent(state.copy(dialog = UsernameLinkSettingsState.Dialog.ScanFailed(QrScanProblem.NotFound("spider.7"))))

    composeTestRule.onNodeWithText("spider.7", substring = true).assertExists()
  }

  private fun setContent(state: UsernameLinkSettingsState) {
    composeTestRule.setContent {
      UsernameLinkSettingsScreen(state = state, onEvent = { events += it })
    }
  }

  companion object {
    private const val LINK = "https://signal.me/#eu/abc"
  }
}
