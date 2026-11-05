/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.advancedprivacy

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
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AdvancedPrivacySettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<AdvancedPrivacySettingsEvent>()
  private val interactionEvents: List<AdvancedPrivacySettingsEvent>
    get() = events.filterNot { it == AdvancedPrivacySettingsEvent.ScreenResumed }

  @Test
  fun `showing the screen emits ScreenResumed and nothing else`() {
    setContent(AdvancedPrivacySettingsState())

    assertThat(events).containsExactly(AdvancedPrivacySettingsEvent.ScreenResumed)
  }

  @Test
  fun `clicking always relay calls emits AlwaysRelayCallsToggled`() = assertClickEmits(
    tag = AdvancedPrivacySettingsTestTags.ROW_ALWAYS_RELAY_CALLS,
    expected = AdvancedPrivacySettingsEvent.AlwaysRelayCallsToggled(true)
  )

  @Test
  fun `clicking censorship circumvention emits CensorshipCircumventionToggled`() = assertClickEmits(
    tag = AdvancedPrivacySettingsTestTags.ROW_CENSORSHIP_CIRCUMVENTION,
    expected = AdvancedPrivacySettingsEvent.CensorshipCircumventionToggled(true)
  )

  @Test
  fun `censorship circumvention cannot be changed while connected`() {
    setContent(AdvancedPrivacySettingsState(censorshipCircumventionState = CensorshipCircumventionState.UNAVAILABLE_CONNECTED))

    composeTestRule.onNodeWithTag(AdvancedPrivacySettingsTestTags.ROW_CENSORSHIP_CIRCUMVENTION).performClick()

    assertThat(interactionEvents).isEmpty()
    composeTestRule.onNodeWithText("Censorship circumvention is not necessary; you are already connected to the Signal service.").assertIsDisplayed()
  }

  @Test
  fun `clicking the sealed sender status icon emits SealedSenderStatusIconToggled`() = assertClickEmits(
    tag = AdvancedPrivacySettingsTestTags.ROW_SEALED_SENDER_STATUS_ICON,
    expected = AdvancedPrivacySettingsEvent.SealedSenderStatusIconToggled(true)
  )

  @Test
  fun `clicking allow from anyone emits SealedSenderFromAnyoneToggled`() = assertClickEmits(
    tag = AdvancedPrivacySettingsTestTags.ROW_SEALED_SENDER_FROM_ANYONE,
    expected = AdvancedPrivacySettingsEvent.SealedSenderFromAnyoneToggled(true)
  )

  @Test
  fun `allow from anyone is hidden on a linked device`() {
    setContent(AdvancedPrivacySettingsState(isPrimaryDevice = false))

    composeTestRule.onNodeWithTag(AdvancedPrivacySettingsTestTags.ROW_SEALED_SENDER_FROM_ANYONE).assertDoesNotExist()
  }

  @Test
  fun `clicking automatic key verification emits AutomaticKeyVerificationToggled`() = assertClickEmits(
    tag = AdvancedPrivacySettingsTestTags.ROW_AUTOMATIC_KEY_VERIFICATION,
    expected = AdvancedPrivacySettingsEvent.AutomaticKeyVerificationToggled(true)
  )

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    setContent(AdvancedPrivacySettingsState())

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(interactionEvents).containsExactly(AdvancedPrivacySettingsEvent.NavigateBackClicked)
  }

  private fun assertClickEmits(tag: String, expected: AdvancedPrivacySettingsEvent) {
    setContent(AdvancedPrivacySettingsState(isPrimaryDevice = true))

    scrollTo(tag)
    composeTestRule.onNodeWithTag(tag).performClick()

    assertThat(interactionEvents).containsExactly(expected)
  }

  private fun setContent(state: AdvancedPrivacySettingsState) {
    composeTestRule.setContent {
      AdvancedPrivacySettingsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(AdvancedPrivacySettingsTestTags.SCROLLER).performScrollToNode(hasTestTag(testTag))
  }
}
