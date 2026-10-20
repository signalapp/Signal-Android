/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HelpSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<HelpSettingsEvent>()

  @Test
  fun `the version name is shown`() {
    setContent(HelpSettingsState(versionName = "8.33.0"))

    scrollTo(HelpSettingsTestTags.ROW_VERSION)
    composeTestRule.onNodeWithText("8.33.0").assertIsDisplayed()
  }

  @Test
  fun `clicking support center emits SupportCenterClicked`() = assertClickEmits(HelpSettingsTestTags.ROW_SUPPORT_CENTER, HelpSettingsEvent.SupportCenterClicked)

  @Test
  fun `clicking contact us emits ContactUsClicked`() = assertClickEmits(HelpSettingsTestTags.ROW_CONTACT_US, HelpSettingsEvent.ContactUsClicked)

  @Test
  fun `clicking debug log emits DebugLogClicked`() = assertClickEmits(HelpSettingsTestTags.ROW_DEBUG_LOG, HelpSettingsEvent.DebugLogClicked)

  @Test
  fun `clicking licenses emits LicensesClicked`() = assertClickEmits(HelpSettingsTestTags.ROW_LICENSES, HelpSettingsEvent.LicensesClicked)

  @Test
  fun `clicking terms and privacy policy emits TermsAndPrivacyPolicyClicked`() = assertClickEmits(HelpSettingsTestTags.ROW_TERMS_AND_PRIVACY_POLICY, HelpSettingsEvent.TermsAndPrivacyPolicyClicked)

  @Test
  fun `long-pressing the version emits VersionLongClicked`() {
    setContent(HelpSettingsState(versionName = "8.33.0"))

    scrollTo(HelpSettingsTestTags.ROW_VERSION)
    composeTestRule.onNodeWithTag(HelpSettingsTestTags.ROW_VERSION).performTouchInput { longClick() }

    assertThat(events).containsExactly(HelpSettingsEvent.VersionLongClicked)
  }

  @Test
  fun `tapping the version does nothing`() {
    setContent(HelpSettingsState(versionName = "8.33.0"))

    scrollTo(HelpSettingsTestTags.ROW_VERSION)
    composeTestRule.onNodeWithTag(HelpSettingsTestTags.ROW_VERSION).performClick()

    assertThat(events).isEmpty()
  }

  private fun assertClickEmits(tag: String, expected: HelpSettingsEvent) {
    setContent(HelpSettingsState(versionName = "8.33.0"))

    scrollTo(tag)
    composeTestRule.onNodeWithTag(tag).performClick()

    assertThat(events).containsExactly(expected)
  }

  private fun setContent(state: HelpSettingsState) {
    composeTestRule.setContent {
      HelpSettingsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(HelpSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }
}
