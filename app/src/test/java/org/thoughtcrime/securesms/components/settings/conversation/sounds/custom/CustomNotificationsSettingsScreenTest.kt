/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.sounds.custom

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.database.RecipientTable.VibrateState

/**
 * Checks which events the custom notifications rows emit, and which rows a given state renders at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CustomNotificationsSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `resuming emits Foregrounded so the channel can be re-checked`() {
    val events = setContent(createState())

    assertThat(events).contains(CustomNotificationsEvents.Foregrounded)
  }

  @Test
  fun `turning custom notifications on emits SetHasCustomNotifications`() {
    val events = setContent(createState(notificationChannel = null))

    click(CustomNotificationsTestTags.CUSTOM_NOTIFICATIONS_TOGGLE)

    assertThat(events.userDriven()).contains(CustomNotificationsEvents.SetHasCustomNotifications(true))
  }

  @Test
  fun `turning custom notifications off emits SetHasCustomNotifications`() {
    val events = setContent(createState())

    click(CustomNotificationsTestTags.CUSTOM_NOTIFICATIONS_TOGGLE)

    assertThat(events.userDriven()).contains(CustomNotificationsEvents.SetHasCustomNotifications(false))
  }

  @Test
  fun `the custom notifications toggle is hidden where channels are unsupported`() {
    setContent(createState(supportsNotificationChannels = false))

    composeTestRule.onNodeWithTag(CustomNotificationsTestTags.CUSTOM_NOTIFICATIONS_TOGGLE).assertDoesNotExist()
  }

  @Test
  fun `no control does anything until the initial load completes`() {
    val events = setContent(createState(isInitialLoadComplete = false))

    click(CustomNotificationsTestTags.CUSTOM_NOTIFICATIONS_TOGGLE)
    click(CustomNotificationsTestTags.MESSAGE_SOUND_ROW)
    click(CustomNotificationsTestTags.CALL_SOUND_ROW)

    assertThat(events.userDriven()).isEmpty()
  }

  @Test
  fun `sound and vibration are handed to the system where channel settings can be opened`() {
    setContent(createState(canOpenChannelSettings = true))

    composeTestRule.onNodeWithTag(CustomNotificationsTestTags.CUSTOMIZE_ROW).assertIsDisplayed()
    composeTestRule.onNodeWithTag(CustomNotificationsTestTags.MESSAGE_SOUND_ROW).assertDoesNotExist()
    composeTestRule.onNodeWithTag(CustomNotificationsTestTags.MESSAGE_VIBRATE_TOGGLE).assertDoesNotExist()
  }

  @Test
  fun `tapping the message sound row emits SelectMessageSound`() {
    val events = setContent(createState())

    click(CustomNotificationsTestTags.MESSAGE_SOUND_ROW)

    assertThat(events.userDriven()).contains(CustomNotificationsEvents.SelectMessageSound)
  }

  @Test
  fun `tapping the call ringtone row emits SelectCallSound`() {
    val events = setContent(createState())

    click(CustomNotificationsTestTags.CALL_SOUND_ROW)

    assertThat(events.userDriven()).contains(CustomNotificationsEvents.SelectCallSound)
  }

  @Test
  fun `the message sound row does nothing while custom notifications are off`() {
    val events = setContent(createState(notificationChannel = null))

    click(CustomNotificationsTestTags.MESSAGE_SOUND_ROW)

    assertThat(events.userDriven()).isEmpty()
  }

  @Test
  fun `toggling message vibration emits SetMessageVibrate`() {
    val events = setContent(createState(messageVibrateEnabled = false))

    click(CustomNotificationsTestTags.MESSAGE_VIBRATE_TOGGLE)

    assertThat(events.userDriven()).contains(CustomNotificationsEvents.SetMessageVibrate(VibrateState.ENABLED))
  }

  @Test
  fun `picking a message vibration option emits SetMessageVibrate`() {
    val events = setContent(createState(supportsNotificationChannels = false))

    click(CustomNotificationsTestTags.MESSAGE_VIBRATE_ROW)
    selectVibrateOption(VibrateState.DISABLED)

    assertThat(events.userDriven()).contains(CustomNotificationsEvents.SetMessageVibrate(VibrateState.DISABLED))
  }

  @Test
  fun `picking a call vibration option emits SetCallVibrate`() {
    val events = setContent(createState())

    click(CustomNotificationsTestTags.CALL_VIBRATE_ROW)
    selectVibrateOption(VibrateState.ENABLED)

    assertThat(events.userDriven()).contains(CustomNotificationsEvents.SetCallVibrate(VibrateState.ENABLED))
  }

  @Test
  fun `the call section is hidden for an unregistered recipient`() {
    setContent(createState(showCallingOptions = false))

    composeTestRule.onNodeWithTag(CustomNotificationsTestTags.CALL_SOUND_ROW).assertDoesNotExist()
    composeTestRule.onNodeWithTag(CustomNotificationsTestTags.CALL_VIBRATE_ROW).assertDoesNotExist()
  }

  private fun createState(
    isInitialLoadComplete: Boolean = true,
    supportsNotificationChannels: Boolean = true,
    canOpenChannelSettings: Boolean = false,
    notificationChannel: String? = "channel",
    messageVibrateEnabled: Boolean = false,
    showCallingOptions: Boolean = true
  ): CustomNotificationsSettingsState {
    return CustomNotificationsSettingsState(
      isInitialLoadComplete = isInitialLoadComplete,
      supportsNotificationChannels = supportsNotificationChannels,
      canOpenChannelSettings = canOpenChannelSettings,
      notificationChannel = notificationChannel,
      messageVibrateEnabled = messageVibrateEnabled,
      showCallingOptions = showCallingOptions
    )
  }

  private fun setContent(state: CustomNotificationsSettingsState): List<CustomNotificationsEvents> {
    val events = mutableListOf<CustomNotificationsEvents>()

    composeTestRule.setContent {
      SignalTheme {
        CustomNotificationsSettingsScreen(
          state = state,
          ringtonePickerRequests = emptyFlow(),
          onEvent = { events += it },
          onNavigationClick = {}
        )
      }
    }

    return events
  }

  private fun click(tag: String) {
    composeTestRule.onNodeWithTag(CustomNotificationsTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
    composeTestRule.onNodeWithTag(tag).performClick()
  }

  /**
   * Picks an option out of an open vibrate dialog, whose options are ordered by [VibrateState.id].
   */
  private fun selectVibrateOption(vibrateState: VibrateState) {
    composeTestRule.onNodeWithTag(Dialogs.testTagRadioListDialogOption(vibrateState.id)).performClick()
  }

  /**
   * Everything the user themselves caused, dropping the [CustomNotificationsEvents.Foregrounded] that the screen emits
   * on its own as soon as it resumes.
   */
  private fun List<CustomNotificationsEvents>.userDriven(): List<CustomNotificationsEvents> {
    return filterNot { it == CustomNotificationsEvents.Foregrounded }
  }
}
