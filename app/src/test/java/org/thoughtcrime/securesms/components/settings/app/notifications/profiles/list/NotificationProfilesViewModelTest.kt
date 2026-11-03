/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.list

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationProfilesViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val profileLists = MutableSharedFlow<NotificationProfilesListRepository.ProfileList>()
  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(NotificationProfilesListRepository)

    every { NotificationProfilesListRepository.observeProfiles() } returns profileLists
  }

  @After
  fun tearDown() {
    unmockkObject(NotificationProfilesListRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `profiles are unknown until the repository emits`() = runTest(testDispatcher) {
    val viewModel = NotificationProfilesViewModel(parentEventEmitter = { navigationEvents += it })

    assertThat(viewModel.state.value).isEqualTo(NotificationProfilesState())
  }

  @Test
  fun `each emission replaces the profiles and the active profile with its status`() = runTest(testDispatcher) {
    val viewModel = NotificationProfilesViewModel(parentEventEmitter = { navigationEvents += it })
    val work = profile(id = 1)
    val sleep = profile(id = 2)

    profileLists.emit(NotificationProfilesListRepository.ProfileList(profiles = listOf(work), activeProfileId = null, activeStatus = null))
    assertThat(viewModel.state.value).isEqualTo(NotificationProfilesState(profiles = listOf(work)))

    profileLists.emit(NotificationProfilesListRepository.ProfileList(profiles = listOf(sleep, work), activeProfileId = 2, activeStatus = NotificationProfiles.ActiveStatus.On))
    assertThat(viewModel.state.value).isEqualTo(NotificationProfilesState(profiles = listOf(sleep, work), activeProfileId = 2, activeStatus = NotificationProfiles.ActiveStatus.On))
  }

  @Test
  fun `NavigateBackClicked navigates back`() = assertEventNavigates(NotificationProfilesEvent.NavigateBackClicked, AppSettingsNavigationEvent.NavigateBack())

  @Test
  fun `NewProfileClicked starts the create flow`() = assertEventNavigates(
    NotificationProfilesEvent.NewProfileClicked,
    AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.EditProfile())
  )

  @Test
  fun `ProfileClicked opens that profile's details`() = assertEventNavigates(
    NotificationProfilesEvent.ProfileClicked(7),
    AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.ProfileDetails(7))
  )

  private fun assertEventNavigates(event: NotificationProfilesEvent, expected: AppSettingsNavigationEvent) = runTest(testDispatcher) {
    val viewModel = NotificationProfilesViewModel(parentEventEmitter = { navigationEvents += it })

    viewModel.onEvent(event)

    assertThat(navigationEvents).containsExactly(expected)
  }

  private fun profile(id: Long): NotificationProfile {
    return NotificationProfile(
      id = id,
      name = "Profile $id",
      emoji = "",
      createdAt = id,
      schedule = NotificationProfileSchedule(id = id),
      notificationProfileId = NotificationProfileId(UUID(0L, id))
    )
  }
}
