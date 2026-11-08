/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.phonenumberprivacy

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.R
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PhoneNumberPrivacySettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<PhoneNumberPrivacySettingsEvent>()

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    setContent(PhoneNumberPrivacySettingsState())

    composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

    assertThat(events).containsExactly(PhoneNumberPrivacySettingsEvent.NavigateBackClicked)
  }

  @Test
  fun `clicking everyone can see my number emits EveryoneCanSeeMyNumberClicked`() {
    setContent(PhoneNumberPrivacySettingsState())

    composeTestRule.onNodeWithTag(PhoneNumberPrivacySettingsTestTags.ROW_EVERYONE_CAN_SEE).performClick()

    assertThat(events).containsExactly(PhoneNumberPrivacySettingsEvent.EveryoneCanSeeMyNumberClicked)
  }

  @Test
  fun `clicking nobody can see my number emits NobodyCanSeeMyNumberClicked`() {
    setContent(PhoneNumberPrivacySettingsState(phoneNumberSharing = true))

    composeTestRule.onNodeWithTag(PhoneNumberPrivacySettingsTestTags.ROW_NOBODY_CAN_SEE).performClick()

    assertThat(events).containsExactly(PhoneNumberPrivacySettingsEvent.NobodyCanSeeMyNumberClicked)
  }

  @Test
  fun `clicking everyone can find me emits EveryoneCanFindMeByNumberClicked`() {
    setContent(PhoneNumberPrivacySettingsState(discoverableByPhoneNumber = false))

    scrollTo(PhoneNumberPrivacySettingsTestTags.ROW_EVERYONE_CAN_FIND)
    composeTestRule.onNodeWithTag(PhoneNumberPrivacySettingsTestTags.ROW_EVERYONE_CAN_FIND).performClick()

    assertThat(events).containsExactly(PhoneNumberPrivacySettingsEvent.EveryoneCanFindMeByNumberClicked)
  }

  @Test
  fun `clicking nobody can find me emits NobodyCanFindMeByNumberClicked even while the number is shared`() {
    setContent(PhoneNumberPrivacySettingsState(phoneNumberSharing = true))

    scrollTo(PhoneNumberPrivacySettingsTestTags.ROW_NOBODY_CAN_FIND)
    composeTestRule.onNodeWithTag(PhoneNumberPrivacySettingsTestTags.ROW_NOBODY_CAN_FIND).performClick()

    assertThat(events).containsExactly(PhoneNumberPrivacySettingsEvent.NobodyCanFindMeByNumberClicked)
  }

  @Test
  fun `the sharing description reflects both settings`() {
    setContent(PhoneNumberPrivacySettingsState(phoneNumberSharing = false, discoverableByPhoneNumber = false))

    composeTestRule.onNodeWithText("Your phone number will not be visible to anyone.").assertIsDisplayed()
  }

  @Test
  fun `the confirmation dialog emits confirm and dismiss events`() {
    setContent(PhoneNumberPrivacySettingsState(dialog = PhoneNumberPrivacySettingsState.Dialog.ConfirmNotDiscoverable))

    composeTestRule.onNodeWithText("Are you sure?").assertIsDisplayed()
    composeTestRule.onNodeWithText("OK").performClick()
    composeTestRule.onNodeWithText("Cancel").performClick()

    assertThat(events).containsExactly(
      PhoneNumberPrivacySettingsEvent.NobodyCanFindMeByNumberConfirmed,
      PhoneNumberPrivacySettingsEvent.DialogDismissed
    )
  }

  @Test
  fun `a snackbar in state is shown and then reported as dismissed`() {
    setContent(PhoneNumberPrivacySettingsState(phoneNumberSharing = true, snackbar = PhoneNumberPrivacySettingsState.Snackbar.HideNumberFirst))

    composeTestRule.onNodeWithText(ApplicationProvider.getApplicationContext<Application>().getString(R.string.PhoneNumberPrivacySettingsFragment__to_change_this_setting)).assertIsDisplayed()

    composeTestRule.waitUntil(timeoutMillis = 10_000) { events.isNotEmpty() }

    assertThat(events).containsExactly(PhoneNumberPrivacySettingsEvent.SnackbarDismissed)
  }

  private fun setContent(state: PhoneNumberPrivacySettingsState) {
    composeTestRule.setContent {
      SignalTheme {
        PhoneNumberPrivacySettingsScreen(
          state = state,
          onEvent = { events += it }
        )
      }
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(PhoneNumberPrivacySettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }
}
