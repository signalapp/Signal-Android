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
import kotlinx.coroutines.flow.Flow
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
    val viewModel = NotificationProfilesViewModel()

    assertThat(viewModel.state.value).isEqualTo(NotificationProfilesState())
  }

  @Test
  fun `each emission replaces the profiles and the active profile with its status`() = runTest(testDispatcher) {
    val viewModel = NotificationProfilesViewModel()
    val work = profile(id = 1)
    val sleep = profile(id = 2)

    profileLists.emit(NotificationProfilesListRepository.ProfileList(profiles = listOf(work), activeProfileId = null, activeStatus = null))
    assertThat(viewModel.state.value).isEqualTo(NotificationProfilesState(profiles = listOf(work)))

    profileLists.emit(NotificationProfilesListRepository.ProfileList(profiles = listOf(sleep, work), activeProfileId = 2, activeStatus = NotificationProfiles.ActiveStatus.On))
    assertThat(viewModel.state.value).isEqualTo(NotificationProfilesState(profiles = listOf(sleep, work), activeProfileId = 2, activeStatus = NotificationProfiles.ActiveStatus.On))
  }

  @Test
  fun `NavigateBackClicked navigates back`() = assertEventEmits(NotificationProfilesEvent.NavigateBackClicked, NotificationProfilesAction.NavigateBack)

  @Test
  fun `NewProfileClicked starts the create flow`() = assertEventEmits(NotificationProfilesEvent.NewProfileClicked, NotificationProfilesAction.NavigateToNewProfile)

  @Test
  fun `ProfileClicked opens that profile's details`() = assertEventEmits(NotificationProfilesEvent.ProfileClicked(7), NotificationProfilesAction.NavigateToProfileDetails(7))

  private fun assertEventEmits(event: NotificationProfilesEvent, expected: NotificationProfilesAction) = runTest(testDispatcher) {
    val viewModel = NotificationProfilesViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(event)

    assertThat(actions).containsExactly(expected)
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

  private fun TestScope.collectActions(actions: Flow<NotificationProfilesAction>): List<NotificationProfilesAction> {
    val collected = mutableListOf<NotificationProfilesAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
