/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications

import android.net.Uri
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.notifications.NotificationsSettingsAction
import org.signal.appsettings.notifications.NotificationsSettingsEvent
import org.signal.appsettings.notifications.NotificationsSettingsState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsSettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()
  private val selfChanges = MutableSharedFlow<Unit>()

  /** What the repository reports as stored, so that a test can change a setting and watch it be read back. */
  private var storedNotifyWhenContactJoinsSignal = false

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(NotificationsSettingsRepository)

    every { NotificationsSettingsRepository.syncFromNotificationChannel() } just Runs
    every { NotificationsSettingsRepository.observeSelfChanges() } returns selfChanges
    coEvery { NotificationsSettingsRepository.isTroubleshootingRecommended() } returns false
    every { NotificationsSettingsRepository.getState(any()) } answers {
      firstArg<NotificationsSettingsState>().copy(notifyWhenContactJoinsSignal = storedNotifyWhenContactJoinsSignal)
    }

    every { NotificationsSettingsRepository.setMessageNotificationsEnabled(any()) } just Runs
    every { NotificationsSettingsRepository.setMessageNotificationsSound(any()) } just Runs
    every { NotificationsSettingsRepository.setMessageNotificationVibration(any()) } just Runs
    every { NotificationsSettingsRepository.setMessageNotificationLedColor(any()) } just Runs
    every { NotificationsSettingsRepository.setMessageNotificationLedBlink(any()) } just Runs
    every { NotificationsSettingsRepository.setMessageNotificationInChatSoundsEnabled(any()) } just Runs
    every { NotificationsSettingsRepository.setMessageRepeatAlerts(any()) } just Runs
    every { NotificationsSettingsRepository.setMessageNotificationPrivacy(any()) } just Runs
    every { NotificationsSettingsRepository.setMessageNotificationPriority(any()) } just Runs
    every { NotificationsSettingsRepository.setCallNotificationsEnabled(any()) } just Runs
    every { NotificationsSettingsRepository.setCallRingtone(any()) } just Runs
    every { NotificationsSettingsRepository.setCallVibrateEnabled(any()) } just Runs
    coEvery { NotificationsSettingsRepository.setNotifyWhenContactJoinsSignal(any()) } answers { storedNotifyWhenContactJoinsSignal = firstArg() }
    coEvery { NotificationsSettingsRepository.setReactionNotificationsEnabled(any()) } just Runs
    coEvery { NotificationsSettingsRepository.setUnreadReminderEnabled(any()) } just Runs
    coEvery { NotificationsSettingsRepository.resetSettings() } just Runs
  }

  @After
  fun tearDown() {
    unmockkObject(NotificationsSettingsRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `creating the view model copies in the notification channel's settings before reading them`() = runTest(testDispatcher) {
    createViewModel()

    verifyOrder {
      NotificationsSettingsRepository.syncFromNotificationChannel()
      NotificationsSettingsRepository.getState(any())
    }
  }

  @Test
  fun `troubleshooting is offered once it is worked out to be recommended`() = runTest(testDispatcher) {
    coEvery { NotificationsSettingsRepository.isTroubleshootingRecommended() } returns true

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.messageNotificationsState.troubleshootNotifications).isTrue()
  }

  @Test
  fun `troubleshooting is not offered when it is not recommended`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.messageNotificationsState.troubleshootNotifications).isFalse()
  }

  @Test
  fun `a change to the local user's recipient reads the settings back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    storedNotifyWhenContactJoinsSignal = true

    selfChanges.emit(Unit)

    assertThat(viewModel.state.value.notifyWhenContactJoinsSignal).isTrue()
  }

  @Test
  fun `ScreenResumed reads the settings back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    storedNotifyWhenContactJoinsSignal = true

    viewModel.onEvent(NotificationsSettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.notifyWhenContactJoinsSignal).isTrue()
  }

  @Test
  fun `changing a setting stores it and reads it back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(NotificationsSettingsEvent.ContactJoinsSignalToggled(true))

    coVerify { NotificationsSettingsRepository.setNotifyWhenContactJoinsSignal(true) }
    assertThat(viewModel.state.value.notifyWhenContactJoinsSignal).isTrue()
  }

  @Test
  fun `each setting is stored through the repository`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val sound = mockk<Uri>()
    val ringtone = mockk<Uri>()

    viewModel.onEvent(NotificationsSettingsEvent.MessageNotificationsToggled(false))
    viewModel.onEvent(NotificationsSettingsEvent.MessagePrivacySelected("contact"))
    viewModel.onEvent(NotificationsSettingsEvent.ReactionNotificationsToggled(false))
    viewModel.onEvent(NotificationsSettingsEvent.UnreadRemindersToggled(false))
    viewModel.onEvent(NotificationsSettingsEvent.RepeatAlertsSelected("3"))
    viewModel.onEvent(NotificationsSettingsEvent.MessageSoundPicked(sound))
    viewModel.onEvent(NotificationsSettingsEvent.MessageVibrateToggled(false))
    viewModel.onEvent(NotificationsSettingsEvent.LedColorSelected("red"))
    viewModel.onEvent(NotificationsSettingsEvent.LedBlinkSelected("500,2000"))
    viewModel.onEvent(NotificationsSettingsEvent.InChatSoundsToggled(false))
    viewModel.onEvent(NotificationsSettingsEvent.PrioritySelected("2"))
    viewModel.onEvent(NotificationsSettingsEvent.CallNotificationsToggled(false))
    viewModel.onEvent(NotificationsSettingsEvent.CallRingtonePicked(ringtone))
    viewModel.onEvent(NotificationsSettingsEvent.CallVibrateToggled(false))

    coVerify {
      NotificationsSettingsRepository.setMessageNotificationsEnabled(false)
      NotificationsSettingsRepository.setMessageNotificationPrivacy("contact")
      NotificationsSettingsRepository.setReactionNotificationsEnabled(false)
      NotificationsSettingsRepository.setUnreadReminderEnabled(false)
      NotificationsSettingsRepository.setMessageRepeatAlerts(3)
      NotificationsSettingsRepository.setMessageNotificationsSound(sound)
      NotificationsSettingsRepository.setMessageNotificationVibration(false)
      NotificationsSettingsRepository.setMessageNotificationLedColor("red")
      NotificationsSettingsRepository.setMessageNotificationLedBlink("500,2000")
      NotificationsSettingsRepository.setMessageNotificationInChatSoundsEnabled(false)
      NotificationsSettingsRepository.setMessageNotificationPriority(2)
      NotificationsSettingsRepository.setCallNotificationsEnabled(false)
      NotificationsSettingsRepository.setCallRingtone(ringtone)
      NotificationsSettingsRepository.setCallVibrateEnabled(false)
    }
  }

  @Test
  fun `a picker returning nothing is still stored, as no sound`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(NotificationsSettingsEvent.MessageSoundPicked(null))
    viewModel.onEvent(NotificationsSettingsEvent.CallRingtonePicked(null))

    verify {
      NotificationsSettingsRepository.setMessageNotificationsSound(null)
      NotificationsSettingsRepository.setCallRingtone(null)
    }
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(NotificationsSettingsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack())
  }

  @Test
  fun `WhileMutedClicked opens the muted notification settings`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(NotificationsSettingsEvent.WhileMutedClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.MutedNotifications))
  }

  @Test
  fun `NotificationProfilesClicked opens notification profiles`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(NotificationsSettingsEvent.NotificationProfilesClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.NotificationProfiles))
  }

  @Test
  fun `TurnOnNotificationsClicked shows how to turn notifications on`() = assertEventEmits(
    NotificationsSettingsEvent.TurnOnNotificationsClicked,
    NotificationsSettingsAction.ShowTurnOnNotificationsSheet
  )

  @Test
  fun `CustomizeClicked opens the message channel's settings`() = assertEventEmits(
    NotificationsSettingsEvent.CustomizeClicked,
    NotificationsSettingsAction.OpenMessageChannelSettings
  )

  @Test
  fun `MessageSoundClicked opens the sound picker`() = assertEventEmits(
    NotificationsSettingsEvent.MessageSoundClicked,
    NotificationsSettingsAction.LaunchMessageSoundPicker
  )

  @Test
  fun `CallRingtoneClicked opens the ringtone picker`() = assertEventEmits(
    NotificationsSettingsEvent.CallRingtoneClicked,
    NotificationsSettingsAction.LaunchCallRingtonePicker
  )

  @Test
  fun `PriorityClicked opens the channel's priority settings`() = assertEventEmits(
    NotificationsSettingsEvent.PriorityClicked,
    NotificationsSettingsAction.LaunchNotificationPrioritySettings
  )

  @Test
  fun `TroubleshootClicked walks through what is delaying notifications`() = assertEventEmits(
    NotificationsSettingsEvent.TroubleshootClicked,
    NotificationsSettingsAction.ShowTroubleshootNotificationsDialog
  )

  @Test
  fun `SoundPickerUnavailable shows a snackbar until it is dismissed`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(NotificationsSettingsEvent.SoundPickerUnavailable)
    assertThat(viewModel.state.value.snackbar).isEqualTo(NotificationsSettingsState.Snackbar.SoundPickerUnavailable)

    viewModel.onEvent(NotificationsSettingsEvent.SnackbarDismissed)
    assertThat(viewModel.state.value.snackbar).isEqualTo(NotificationsSettingsState.Snackbar.None)
  }

  @Test
  fun `ResetClicked asks for confirmation without resetting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(NotificationsSettingsEvent.ResetClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(NotificationsSettingsState.Dialog.ConfirmReset)
    coVerify(exactly = 0) { NotificationsSettingsRepository.resetSettings() }
  }

  @Test
  fun `confirming the reset closes the dialog and resets every setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    viewModel.onEvent(NotificationsSettingsEvent.ResetClicked)

    viewModel.onEvent(NotificationsSettingsEvent.ResetConfirmed)

    assertThat(viewModel.state.value.dialog).isEqualTo(NotificationsSettingsState.Dialog.None)
    coVerify { NotificationsSettingsRepository.resetSettings() }
  }

  @Test
  fun `DialogDismissed closes the dialog without resetting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    viewModel.onEvent(NotificationsSettingsEvent.ResetClicked)

    viewModel.onEvent(NotificationsSettingsEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(NotificationsSettingsState.Dialog.None)
    coVerify(exactly = 0) { NotificationsSettingsRepository.resetSettings() }
  }

  @Test
  fun `reading the settings back keeps the dialog and snackbar showing`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    viewModel.onEvent(NotificationsSettingsEvent.ResetClicked)
    viewModel.onEvent(NotificationsSettingsEvent.SoundPickerUnavailable)

    viewModel.onEvent(NotificationsSettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.dialog).isEqualTo(NotificationsSettingsState.Dialog.ConfirmReset)
    assertThat(viewModel.state.value.snackbar).isEqualTo(NotificationsSettingsState.Snackbar.SoundPickerUnavailable)
  }

  @Test
  fun `picked sounds are logged without their URI, which can name a file the user added`() {
    val sound = mockk<Uri> { every { this@mockk.toString() } returns "content://media/external/audio/media/57?title=Mom saying hi" }

    assertThat(NotificationsSettingsEvent.MessageSoundPicked(sound).toString()).isEqualTo("MessageSoundPicked(hasSound=true)")
    assertThat(NotificationsSettingsEvent.CallRingtonePicked(null).toString()).isEqualTo("CallRingtonePicked(hasRingtone=false)")
  }

  private fun assertEventEmits(event: NotificationsSettingsEvent, expected: NotificationsSettingsAction) = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(event)

    assertThat(actions).containsExactly(expected)
    assertThat(navigationEvents).isEmpty()
  }

  private fun createViewModel(): NotificationsSettingsViewModel {
    return NotificationsSettingsViewModel(parentEventEmitter = { navigationEvents += it })
  }

  private fun TestScope.collectActions(viewModel: NotificationsSettingsViewModel): List<NotificationsSettingsAction> {
    val collected = mutableListOf<NotificationsSettingsAction>()
    backgroundScope.launch { viewModel.actions.toList(collected) }
    return collected
  }
}
