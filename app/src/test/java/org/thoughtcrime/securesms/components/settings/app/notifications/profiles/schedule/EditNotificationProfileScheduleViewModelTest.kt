/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.schedule

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.PopUpTo
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class EditNotificationProfileScheduleViewModelTest {

  companion object {
    private const val PROFILE_ID = 3L
    private val SCHEDULE = NotificationProfileSchedule(id = PROFILE_ID, enabled = true, start = 900, end = 1700, daysEnabled = setOf(DayOfWeek.MONDAY))
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  /** Finishing the create flow opens the "created" screen in place of every screen the flow opened. */
  private val profileCreated = AppSettingsNavigationEvent.NavigateToScreen(
    route = AppSettingsRoute.NotificationsRoute.Created(PROFILE_ID),
    popUpTo = PopUpTo(AppSettingsRoute.NotificationsRoute.AddAllowedMembers::class, inclusive = true)
  )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(EditNotificationProfileScheduleRepository)

    coEvery { EditNotificationProfileScheduleRepository.getSchedule(PROFILE_ID) } returns SCHEDULE
    coEvery { EditNotificationProfileScheduleRepository.saveSchedule(PROFILE_ID, any()) } just Runs
  }

  @After
  fun tearDown() {
    unmockkObject(EditNotificationProfileScheduleRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `the schedule is read out of the repository`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(EditNotificationProfileScheduleState(schedule = SCHEDULE))
  }

  @Test
  fun `ScheduleToggled turns the schedule on and off`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileScheduleEvent.ScheduleToggled(false))

    assertThat(viewModel.state.value.schedule?.enabled).isEqualTo(false)
  }

  @Test
  fun `DayToggled adds and removes days`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileScheduleEvent.DayToggled(DayOfWeek.FRIDAY))
    assertThat(viewModel.state.value.schedule?.daysEnabled).isEqualTo(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))

    viewModel.onEvent(EditNotificationProfileScheduleEvent.DayToggled(DayOfWeek.MONDAY))
    assertThat(viewModel.state.value.schedule?.daysEnabled).isEqualTo(setOf(DayOfWeek.FRIDAY))
  }

  @Test
  fun `picking a start time updates the start and closes the picker`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileScheduleEvent.StartTimeClicked)
    assertThat(viewModel.state.value.dialog).isEqualTo(EditNotificationProfileScheduleState.Dialog.TimePicker(isStart = true))

    viewModel.onEvent(EditNotificationProfileScheduleEvent.TimeSelected(LocalTime.of(21, 30)))

    assertThat(viewModel.state.value.schedule?.start).isEqualTo(2130)
    assertThat(viewModel.state.value.schedule?.end).isEqualTo(1700)
    assertThat(viewModel.state.value.dialog).isEqualTo(EditNotificationProfileScheduleState.Dialog.None)
  }

  @Test
  fun `picking an end time updates the end`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileScheduleEvent.EndTimeClicked)
    viewModel.onEvent(EditNotificationProfileScheduleEvent.TimeSelected(LocalTime.of(6, 5)))

    assertThat(viewModel.state.value.schedule?.end).isEqualTo(605)
    assertThat(viewModel.state.value.schedule?.start).isEqualTo(900)
  }

  @Test
  fun `the time picker doesn't open while the schedule is off`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileScheduleEvent.ScheduleToggled(false))
    viewModel.onEvent(EditNotificationProfileScheduleEvent.StartTimeClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(EditNotificationProfileScheduleState.Dialog.None)
  }

  @Test
  fun `DialogDismissed closes the picker without changing the schedule`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileScheduleEvent.StartTimeClicked)
    viewModel.onEvent(EditNotificationProfileScheduleEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(EditNotificationProfileScheduleState.Dialog.None)
    assertThat(viewModel.state.value.schedule).isEqualTo(SCHEDULE)
  }

  @Test
  fun `an enabled schedule with no days can't be saved`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileScheduleEvent.DayToggled(DayOfWeek.MONDAY))
    viewModel.onEvent(EditNotificationProfileScheduleEvent.NextClicked)

    assertThat(viewModel.state.value.snackbar).isEqualTo(EditNotificationProfileScheduleState.Snackbar.NoDaysSelected)
    assertThat(navigationEvents).isEmpty()
    coVerify(exactly = 0) { EditNotificationProfileScheduleRepository.saveSchedule(any(), any()) }
  }

  @Test
  fun `SnackbarDismissed clears the snackbar`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileScheduleEvent.DayToggled(DayOfWeek.MONDAY))
    viewModel.onEvent(EditNotificationProfileScheduleEvent.NextClicked)
    viewModel.onEvent(EditNotificationProfileScheduleEvent.SnackbarDismissed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(EditNotificationProfileScheduleState.Snackbar.None)
  }

  @Test
  fun `skipping the schedule in create mode finishes the flow without saving`() = runTest(testDispatcher) {
    val viewModel = createViewModel(createMode = true)

    viewModel.onEvent(EditNotificationProfileScheduleEvent.ScheduleToggled(false))
    viewModel.onEvent(EditNotificationProfileScheduleEvent.NextClicked)

    coVerify(exactly = 0) { EditNotificationProfileScheduleRepository.saveSchedule(any(), any()) }
    assertThat(navigationEvents).containsExactly(profileCreated)
  }

  @Test
  fun `saving in create mode finishes the flow`() = runTest(testDispatcher) {
    val viewModel = createViewModel(createMode = true)

    viewModel.onEvent(EditNotificationProfileScheduleEvent.NextClicked)

    coVerify(exactly = 1) { EditNotificationProfileScheduleRepository.saveSchedule(PROFILE_ID, SCHEDULE) }
    assertThat(navigationEvents).containsExactly(profileCreated)
  }

  @Test
  fun `saving outside create mode goes back, even with the schedule off`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileScheduleEvent.ScheduleToggled(false))
    viewModel.onEvent(EditNotificationProfileScheduleEvent.NextClicked)

    coVerify(exactly = 1) { EditNotificationProfileScheduleRepository.saveSchedule(PROFILE_ID, SCHEDULE.copy(enabled = false)) }
    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack())
  }

  @Test
  fun `the button is disabled while saving`() = runTest(testDispatcher) {
    val saved = CompletableDeferred<Unit>()
    coEvery { EditNotificationProfileScheduleRepository.saveSchedule(PROFILE_ID, any()) } coAnswers { saved.await() }

    val viewModel = createViewModel()
    viewModel.onEvent(EditNotificationProfileScheduleEvent.NextClicked)

    assertThat(viewModel.state.value.isSaving).isTrue()
    assertThat(viewModel.state.value.canSave).isFalse()

    saved.complete(Unit)
    assertThat(viewModel.state.value.isSaving).isFalse()
  }

  @Test
  fun `nothing can be saved until the schedule has loaded`() = runTest(testDispatcher) {
    val loaded = CompletableDeferred<NotificationProfileSchedule?>()
    coEvery { EditNotificationProfileScheduleRepository.getSchedule(PROFILE_ID) } coAnswers { loaded.await() }

    val viewModel = createViewModel()
    viewModel.onEvent(EditNotificationProfileScheduleEvent.NextClicked)

    assertThat(viewModel.state.value.schedule).isNull()
    assertThat(viewModel.state.value.canSave).isFalse()
    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileScheduleEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack())
  }

  private fun createViewModel(createMode: Boolean = false): EditNotificationProfileScheduleViewModel {
    return EditNotificationProfileScheduleViewModel(profileId = PROFILE_ID, createMode = createMode, parentEventEmitter = { navigationEvents += it })
  }
}
