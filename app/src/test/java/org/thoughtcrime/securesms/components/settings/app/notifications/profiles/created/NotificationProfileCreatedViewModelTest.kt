/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.created

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
import org.signal.appsettings.notificationprofilecreated.NotificationProfileCreatedEvent
import org.signal.appsettings.notificationprofilecreated.NotificationProfileCreatedState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.PopUpTo
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationProfileCreatedViewModelTest {

  companion object {
    private const val PROFILE_ID = 12L
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(NotificationProfileCreatedRepository)

    coEvery { NotificationProfileCreatedRepository.hasSchedule(PROFILE_ID) } returns true
  }

  @After
  fun tearDown() {
    unmockkObject(NotificationProfileCreatedRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `a scheduled profile is read out of the repository`() = runTest(testDispatcher) {
    val viewModel = NotificationProfileCreatedViewModel(PROFILE_ID, parentEventEmitter = { navigationEvents += it })

    assertThat(viewModel.state.value).isEqualTo(NotificationProfileCreatedState(hasSchedule = true))
  }

  @Test
  fun `an unscheduled profile is read out of the repository`() = runTest(testDispatcher) {
    coEvery { NotificationProfileCreatedRepository.hasSchedule(PROFILE_ID) } returns false

    val viewModel = NotificationProfileCreatedViewModel(PROFILE_ID, parentEventEmitter = { navigationEvents += it })

    assertThat(viewModel.state.value).isEqualTo(NotificationProfileCreatedState(hasSchedule = false))
  }

  @Test
  fun `a missing profile leaves the tips hidden`() = runTest(testDispatcher) {
    coEvery { NotificationProfileCreatedRepository.hasSchedule(PROFILE_ID) } returns null

    val viewModel = NotificationProfileCreatedViewModel(PROFILE_ID, parentEventEmitter = { navigationEvents += it })

    assertThat(viewModel.state.value.hasSchedule).isNull()
  }

  @Test
  fun `DoneClicked navigates to the profile's details`() = runTest(testDispatcher) {
    val viewModel = NotificationProfileCreatedViewModel(PROFILE_ID, parentEventEmitter = { navigationEvents += it })

    viewModel.onEvent(NotificationProfileCreatedEvent.DoneClicked)

    assertThat(navigationEvents).containsExactly(
      AppSettingsNavigationEvent.NavigateToScreen(
        route = AppSettingsRoute.NotificationsRoute.ProfileDetails(PROFILE_ID),
        popUpTo = PopUpTo(AppSettingsRoute.NotificationsRoute.Created::class, inclusive = true)
      )
    )
  }
}
