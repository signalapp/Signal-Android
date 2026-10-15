/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.screenlock

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.compose.Dialogs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ScreenLockSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<ScreenLockSettingsEvent>()

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    setContent(ScreenLockSettingsState())

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(events).containsExactly(ScreenLockSettingsEvent.NavigateBackClicked)
  }

  @Test
  fun `turning on screen lock emits ScreenLockToggled once authenticated`() {
    setContent(ScreenLockSettingsState(isScreenLockEnabled = false))

    composeTestRule.onNodeWithTag(ScreenLockSettingsTestTags.SWITCH_SCREEN_LOCK).performClick()

    assertThat(events).containsExactly(ScreenLockSettingsEvent.ScreenLockToggled(true))
  }

  @Test
  fun `turning off screen lock emits ScreenLockToggled once authenticated`() {
    setContent(ScreenLockSettingsState(isScreenLockEnabled = true))

    composeTestRule.onNodeWithTag(ScreenLockSettingsTestTags.SWITCH_SCREEN_LOCK).performClick()

    assertThat(events).containsExactly(ScreenLockSettingsEvent.ScreenLockToggled(false))
  }

  @Test
  fun `the switch is disabled when the device cannot authenticate`() {
    setContent(ScreenLockSettingsState(canAuthenticate = false))

    composeTestRule.onNodeWithTag(ScreenLockSettingsTestTags.SWITCH_SCREEN_LOCK).assertIsNotEnabled()
  }

  @Test
  fun `timeout options are hidden while screen lock is off`() {
    setContent(ScreenLockSettingsState(isScreenLockEnabled = false))

    composeTestRule.onNodeWithTag(ScreenLockSettingsTestTags.ROW_CUSTOM_TIMEOUT).assertDoesNotExist()
  }

  @Test
  fun `picking a preset timeout emits TimeoutSelected`() {
    setContent(ScreenLockSettingsState(isScreenLockEnabled = true, timeout = 1.minutes))

    scrollTo(ScreenLockSettingsTestTags.ROW_TIMEOUT_PREFIX + 5.minutes.inWholeSeconds)
    composeTestRule.onNodeWithTag(ScreenLockSettingsTestTags.ROW_TIMEOUT_PREFIX + 5.minutes.inWholeSeconds).performClick()

    assertThat(events).containsExactly(ScreenLockSettingsEvent.TimeoutSelected(5.minutes))
  }

  @Test
  fun `a custom timeout selects the custom row and shows its value`() {
    setContent(ScreenLockSettingsState(isScreenLockEnabled = true, timeout = 3.hours))

    scrollTo(ScreenLockSettingsTestTags.ROW_CUSTOM_TIMEOUT)
    composeTestRule.onNodeWithTag(ScreenLockSettingsTestTags.ROW_CUSTOM_TIMEOUT).assertIsSelected()
    composeTestRule.onNodeWithText("3 hours").assertIsDisplayed()
  }

  @Test
  fun `clicking custom time emits CustomTimeoutClicked`() {
    setContent(ScreenLockSettingsState(isScreenLockEnabled = true, timeout = 1.minutes))

    scrollTo(ScreenLockSettingsTestTags.ROW_CUSTOM_TIMEOUT)
    composeTestRule.onNodeWithTag(ScreenLockSettingsTestTags.ROW_CUSTOM_TIMEOUT).performClick()

    assertThat(events).containsExactly(ScreenLockSettingsEvent.CustomTimeoutClicked)
  }

  @Test
  fun `confirming the custom timeout picker emits the picked timeout`() {
    setContent(ScreenLockSettingsState(isScreenLockEnabled = true, timeout = 3.hours, dialog = ScreenLockSettingsState.Dialog.CustomTimeout))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(events).containsExactly(ScreenLockSettingsEvent.CustomTimeoutSelected(3.hours))
  }

  @Test
  fun `cancelling the custom timeout picker emits DialogDismissed`() {
    setContent(ScreenLockSettingsState(isScreenLockEnabled = true, timeout = 3.hours, dialog = ScreenLockSettingsState.Dialog.CustomTimeout))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON).performClick()

    assertThat(events).containsExactly(ScreenLockSettingsEvent.DialogDismissed)
  }

  @Test
  fun `the timeout options form a single radio group for accessibility services`() {
    setContent(ScreenLockSettingsState(isScreenLockEnabled = true, timeout = 1.minutes))

    val inGroup = hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
    val timeoutTags = ScreenLockSettingsState.PRESET_TIMEOUTS.map { ScreenLockSettingsTestTags.ROW_TIMEOUT_PREFIX + it.inWholeSeconds } + ScreenLockSettingsTestTags.ROW_CUSTOM_TIMEOUT
    timeoutTags.forEach { tag ->
      scrollTo(tag)
      composeTestRule.onNode(hasTestTag(tag) and inGroup).assertExists()
    }
  }

  @Test
  fun `nothing is emitted just by showing the screen`() {
    setContent(ScreenLockSettingsState(isScreenLockEnabled = true, timeout = 3.hours))

    assertThat(events).isEmpty()
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(ScreenLockSettingsTestTags.SCROLLER).performScrollToNode(hasTestTag(testTag))
  }

  private fun setContent(state: ScreenLockSettingsState) {
    composeTestRule.setContent {
      // The biometrics helper needs a FragmentActivity to show its prompt. Inspection mode makes it pass straight through.
      CompositionLocalProvider(LocalInspectionMode provides true) {
        ScreenLockSettingsScreen(
          state = state,
          onEvent = { events += it }
        )
      }
    }
  }
}
