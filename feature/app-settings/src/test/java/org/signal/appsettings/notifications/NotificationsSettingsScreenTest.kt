/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.notifications

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotificationsSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val context: Application = ApplicationProvider.getApplicationContext()

  private val events = mutableListOf<NotificationsSettingsEvent>()

  /** Everything but the [NotificationsSettingsEvent.ScreenResumed] the screen sends of its own accord when it's shown. */
  private val interactionEvents: List<NotificationsSettingsEvent>
    get() = events.filterNot { it == NotificationsSettingsEvent.ScreenResumed }

  @Test
  fun `showing the screen reports that it resumed and nothing else`() {
    setContent(notificationsSettingsPreviewState)

    assertThat(events).containsExactly(NotificationsSettingsEvent.ScreenResumed)
  }

  @Test
  fun `the toolbar's navigation icon emits NavigateBackClicked`() {
    setContent(notificationsSettingsPreviewState)

    composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

    assertThat(interactionEvents).containsExactly(NotificationsSettingsEvent.NavigateBackClicked)
  }

  @Test
  fun `flipping message notifications emits MessageNotificationsToggled`() {
    setContent(notificationsSettingsPreviewState)

    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_MESSAGE_NOTIFICATIONS).performClick()

    assertThat(interactionEvents).containsExactly(NotificationsSettingsEvent.MessageNotificationsToggled(false))
  }

  @Test
  fun `tapping while muted emits WhileMutedClicked`() {
    setContent(notificationsSettingsPreviewState)

    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_WHILE_MUTED).performClick()

    assertThat(interactionEvents).containsExactly(NotificationsSettingsEvent.WhileMutedClicked)
  }

  @Test
  fun `flipping the contact joins toggle emits ContactJoinsSignalToggled`() {
    setContent(notificationsSettingsPreviewState)

    scrollTo(NotificationsSettingsTestTags.ROW_CONTACT_JOINS)
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_CONTACT_JOINS).performClick()

    assertThat(interactionEvents).containsExactly(NotificationsSettingsEvent.ContactJoinsSignalToggled(false))
  }

  @Test
  fun `the system's channel settings own sound and vibration from API 30`() {
    setContent(stateFor(apiLevel = 30, supportsNotificationChannels = true))

    scrollTo(NotificationsSettingsTestTags.ROW_CUSTOMIZE)
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_CUSTOMIZE).performClick()

    assertThat(interactionEvents).containsExactly(NotificationsSettingsEvent.CustomizeClicked)
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_MESSAGE_SOUND).assertDoesNotExist()
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_MESSAGE_VIBRATE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_LED_COLOR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_PRIORITY).assertDoesNotExist()
  }

  @Test
  fun `before API 30 the sound is picked here and shows its name`() {
    setContent(stateFor(apiLevel = 29, supportsNotificationChannels = true))

    scrollTo(NotificationsSettingsTestTags.ROW_MESSAGE_SOUND)
    composeTestRule.onNodeWithText("Chime").assertIsDisplayed()
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_MESSAGE_SOUND).performClick()

    assertThat(interactionEvents).containsExactly(NotificationsSettingsEvent.MessageSoundClicked)
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_CUSTOMIZE).assertDoesNotExist()
  }

  @Test
  fun `with notification channels before API 30, priority opens the channel's settings and there is no blink pattern`() {
    setContent(stateFor(apiLevel = 29, supportsNotificationChannels = true))

    scrollTo(NotificationsSettingsTestTags.ROW_PRIORITY)
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_PRIORITY).performClick()

    assertThat(interactionEvents).containsExactly(NotificationsSettingsEvent.PriorityClicked)
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_LED_BLINK).assertDoesNotExist()
  }

  @Test
  fun `without notification channels, the blink pattern is offered`() {
    setContent(stateFor(apiLevel = 25, supportsNotificationChannels = false))

    scrollTo(NotificationsSettingsTestTags.ROW_LED_BLINK)
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_LED_BLINK).assertIsDisplayed()
  }

  @Test
  fun `troubleshooting is only offered when it is recommended`() {
    setContent(notificationsSettingsPreviewState.copy(messageNotificationsState = notificationsSettingsPreviewState.messageNotificationsState.copy(troubleshootNotifications = false)))

    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_TROUBLESHOOT).assertDoesNotExist()
  }

  @Test
  fun `tapping troubleshoot emits TroubleshootClicked`() {
    setContent(notificationsSettingsPreviewState)

    scrollTo(NotificationsSettingsTestTags.ROW_TROUBLESHOOT)
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_TROUBLESHOOT).performClick()

    assertThat(interactionEvents).containsExactly(NotificationsSettingsEvent.TroubleshootClicked)
  }

  @Test
  fun `tapping the ringtone emits CallRingtoneClicked`() {
    setContent(notificationsSettingsPreviewState)

    scrollTo(NotificationsSettingsTestTags.ROW_CALL_RINGTONE)
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_CALL_RINGTONE).performClick()

    assertThat(interactionEvents).containsExactly(NotificationsSettingsEvent.CallRingtoneClicked)
  }

  @Test
  fun `tapping notification profiles emits NotificationProfilesClicked`() {
    setContent(notificationsSettingsPreviewState)

    scrollTo(NotificationsSettingsTestTags.ROW_NOTIFICATION_PROFILES)
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_NOTIFICATION_PROFILES).performClick()

    assertThat(interactionEvents).containsExactly(NotificationsSettingsEvent.NotificationProfilesClicked)
  }

  @Test
  fun `tapping reset emits ResetClicked`() {
    setContent(notificationsSettingsPreviewState)

    scrollTo(NotificationsSettingsTestTags.ROW_RESET)
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_RESET).performClick()

    assertThat(interactionEvents).containsExactly(NotificationsSettingsEvent.ResetClicked)
  }

  @Test
  fun `confirming the reset dialog emits ResetConfirmed`() {
    setContent(notificationsSettingsPreviewState.copy(dialog = NotificationsSettingsState.Dialog.ConfirmReset))

    composeTestRule.onNodeWithText(context.getString(R.string.NotificationsSettingsFragment__reset_confirm)).performClick()

    assertThat(interactionEvents).containsExactly(NotificationsSettingsEvent.ResetConfirmed)
  }

  @Test
  fun `cancelling the reset dialog emits DialogDismissed`() {
    setContent(notificationsSettingsPreviewState.copy(dialog = NotificationsSettingsState.Dialog.ConfirmReset))

    composeTestRule.onNodeWithText(context.getString(android.R.string.cancel)).performClick()

    assertThat(interactionEvents).containsExactly(NotificationsSettingsEvent.DialogDismissed)
  }

  @Test
  fun `when the system has notifications off, the banner turns them on and the toggles are disabled`() {
    setContent(
      notificationsSettingsPreviewState.copy(
        messageNotificationsState = notificationsSettingsPreviewState.messageNotificationsState.copy(notificationsEnabled = false, canEnableNotifications = false)
      )
    )

    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_MESSAGE_NOTIFICATIONS).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_REACTIONS).assertIsNotEnabled()
    composeTestRule.onNodeWithText(context.getString(R.string.NotificationSettingsFragment__turn_on)).performClick()

    assertThat(interactionEvents).containsExactly(NotificationsSettingsEvent.TurnOnNotificationsClicked)
  }

  @Test
  fun `with message notifications off, the settings that depend on them are disabled`() {
    setContent(
      notificationsSettingsPreviewState.copy(
        messageNotificationsState = notificationsSettingsPreviewState.messageNotificationsState.copy(notificationsEnabled = false)
      )
    )

    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_MESSAGE_NOTIFICATIONS).assertIsEnabled()
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_WHILE_MUTED).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.ROW_REACTIONS).assertIsEnabled()
  }

  private fun stateFor(apiLevel: Int, supportsNotificationChannels: Boolean): NotificationsSettingsState {
    return notificationsSettingsPreviewState.copy(deviceState = DeviceState(apiLevel = apiLevel, supportsNotificationChannels = supportsNotificationChannels))
  }

  private fun setContent(state: NotificationsSettingsState) {
    composeTestRule.setContent {
      NotificationsSettingsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(NotificationsSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }
}
