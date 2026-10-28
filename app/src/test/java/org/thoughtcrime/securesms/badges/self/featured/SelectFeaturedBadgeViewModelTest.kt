/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.featured

import android.app.Application
import android.net.Uri
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.reactivex.rxjava3.subjects.BehaviorSubject
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.badges.SelectFeaturedBadgeEvent
import org.signal.appsettings.badges.SelectFeaturedBadgeState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.models.badges.Badge
import org.signal.core.models.recipients.RecipientId
import org.signal.network.NetworkResult
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SelectFeaturedBadgeViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val selfSubject = BehaviorSubject.create<Recipient>()
  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  private val first = createBadge("first")
  private val second = createBadge("second")
  private val expired = createBadge("expired", expirationTimestamp = 1)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    mockkObject(BadgeRepository)
    mockkObject(Recipient.Companion)
    every { Recipient.self() } returns createSelf(emptyList())
    every { Recipient.observable(SELF_ID) } returns selfSubject

    coEvery { BadgeRepository.setFeaturedBadge(any()) } returns NetworkResult.Success(Unit)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  @Test
  fun `initial state selects the first unexpired badge`() = runTest(testDispatcher) {
    val viewModel = createViewModel(badges = listOf(first, expired, second))

    val state = viewModel.state.value
    assertThat(state.stage).isEqualTo(SelectFeaturedBadgeState.Stage.READY)
    assertThat(state.badges).containsExactly(first, second)
    assertThat(state.selectedBadge).isEqualTo(first)
  }

  @Test
  fun `an unresolved self recipient is ignored`() = runTest(testDispatcher) {
    val viewModel = SelectFeaturedBadgeViewModel(parentEventEmitter = { navigationEvents += it })

    selfSubject.onNext(createSelf(listOf(first), isResolving = true))

    val state = viewModel.state.value
    assertThat(state.stage).isEqualTo(SelectFeaturedBadgeState.Stage.INIT)
    assertThat(state.badges).isEmpty()
  }

  @Test
  fun `BadgeClicked updates the selected badge`() = runTest(testDispatcher) {
    val viewModel = createViewModel(badges = listOf(first, second))

    viewModel.onEvent(SelectFeaturedBadgeEvent.BadgeClicked(second))

    assertThat(viewModel.state.value.selectedBadge).isEqualTo(second)
  }

  @Test
  fun `Save features the selected badge and navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel(badges = listOf(first, second))

    viewModel.onEvent(SelectFeaturedBadgeEvent.BadgeClicked(second))
    viewModel.onEvent(SelectFeaturedBadgeEvent.SaveClicked)

    coVerify(exactly = 1) { BadgeRepository.setFeaturedBadge(second) }
    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `a failed Save returns to READY and shows a snackbar until it's dismissed`() = runTest(testDispatcher) {
    coEvery { BadgeRepository.setFeaturedBadge(any()) } returns NetworkResult.NetworkError(IOException())

    val viewModel = createViewModel(badges = listOf(first))

    viewModel.onEvent(SelectFeaturedBadgeEvent.SaveClicked)

    assertThat(viewModel.state.value.stage).isEqualTo(SelectFeaturedBadgeState.Stage.READY)
    assertThat(viewModel.state.value.snackbar).isEqualTo(SelectFeaturedBadgeState.Snackbar.FailedToUpdateProfile)
    assertThat(navigationEvents).isEmpty()

    viewModel.onEvent(SelectFeaturedBadgeEvent.SnackbarDismissed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(SelectFeaturedBadgeState.Snackbar.None)
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel(badges = listOf(first))

    viewModel.onEvent(SelectFeaturedBadgeEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `Save does nothing without a selected badge`() = runTest(testDispatcher) {
    val viewModel = createViewModel(badges = emptyList())

    viewModel.onEvent(SelectFeaturedBadgeEvent.SaveClicked)

    assertThat(viewModel.state.value.selectedBadge).isNull()
    assertThat(viewModel.state.value.stage).isEqualTo(SelectFeaturedBadgeState.Stage.READY)
    coVerify(exactly = 0) { BadgeRepository.setFeaturedBadge(any()) }
  }

  @Test
  fun `Save does nothing before the screen is ready`() = runTest(testDispatcher) {
    val viewModel = SelectFeaturedBadgeViewModel(parentEventEmitter = { navigationEvents += it })

    viewModel.onEvent(SelectFeaturedBadgeEvent.BadgeClicked(first))
    viewModel.onEvent(SelectFeaturedBadgeEvent.SaveClicked)

    assertThat(viewModel.state.value.stage).isEqualTo(SelectFeaturedBadgeState.Stage.INIT)
    coVerify(exactly = 0) { BadgeRepository.setFeaturedBadge(any()) }
  }

  @Test
  fun `a second Save after success is ignored`() = runTest(testDispatcher) {
    val viewModel = createViewModel(badges = listOf(first))

    viewModel.onEvent(SelectFeaturedBadgeEvent.SaveClicked)
    viewModel.onEvent(SelectFeaturedBadgeEvent.SaveClicked)

    coVerify(exactly = 1) { BadgeRepository.setFeaturedBadge(first) }
  }

  private fun createViewModel(badges: List<Badge>): SelectFeaturedBadgeViewModel {
    val viewModel = SelectFeaturedBadgeViewModel(parentEventEmitter = { navigationEvents += it })
    selfSubject.onNext(createSelf(badges))
    return viewModel
  }

  private fun createSelf(badges: List<Badge>, isResolving: Boolean = false): Recipient {
    return Recipient(id = SELF_ID, isResolving = isResolving, badges = badges)
  }

  private fun createBadge(id: String, expirationTimestamp: Long = 0): Badge {
    return Badge(
      id = id,
      category = Badge.Category.Donor,
      name = id,
      description = "",
      imageUrl = Uri.EMPTY,
      imageDensity = "xxxhdpi",
      expirationTimestamp = expirationTimestamp,
      visible = true,
      duration = 0
    )
  }

  companion object {
    private val SELF_ID = RecipientId.from(1)
  }
}
