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
import kotlinx.coroutines.flow.Flow
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
    assertThat(state.allUnlockedBadges).containsExactly(first, second)
    assertThat(state.selectedBadge).isEqualTo(first)
  }

  @Test
  fun `an unresolved self recipient is ignored`() = runTest(testDispatcher) {
    val viewModel = SelectFeaturedBadgeViewModel()

    selfSubject.onNext(createSelf(listOf(first), isResolving = true))

    val state = viewModel.state.value
    assertThat(state.stage).isEqualTo(SelectFeaturedBadgeState.Stage.INIT)
    assertThat(state.allUnlockedBadges).isEmpty()
  }

  @Test
  fun `BadgeSelected updates the selected badge`() = runTest(testDispatcher) {
    val viewModel = createViewModel(badges = listOf(first, second))

    viewModel.onEvent(SelectFeaturedBadgeEvents.BadgeSelected(second))

    assertThat(viewModel.state.value.selectedBadge).isEqualTo(second)
  }

  @Test
  fun `Save features the selected badge and moves to SAVED`() = runTest(testDispatcher) {
    val viewModel = createViewModel(badges = listOf(first, second))

    viewModel.onEvent(SelectFeaturedBadgeEvents.BadgeSelected(second))
    viewModel.onEvent(SelectFeaturedBadgeEvents.Save)

    coVerify(exactly = 1) { BadgeRepository.setFeaturedBadge(second) }
    assertThat(viewModel.state.value.stage).isEqualTo(SelectFeaturedBadgeState.Stage.SAVED)
  }

  @Test
  fun `a failed Save returns to READY and tells the user`() = runTest(testDispatcher) {
    coEvery { BadgeRepository.setFeaturedBadge(any()) } returns NetworkResult.NetworkError(IOException())

    val viewModel = createViewModel(badges = listOf(first))
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(SelectFeaturedBadgeEvents.Save)

    assertThat(viewModel.state.value.stage).isEqualTo(SelectFeaturedBadgeState.Stage.READY)
    assertThat(actions).containsExactly(SelectFeaturedBadgeActions.ShowFailedToUpdateProfile)
  }

  @Test
  fun `Save does nothing without a selected badge`() = runTest(testDispatcher) {
    val viewModel = createViewModel(badges = emptyList())

    viewModel.onEvent(SelectFeaturedBadgeEvents.Save)

    assertThat(viewModel.state.value.selectedBadge).isNull()
    assertThat(viewModel.state.value.stage).isEqualTo(SelectFeaturedBadgeState.Stage.READY)
    coVerify(exactly = 0) { BadgeRepository.setFeaturedBadge(any()) }
  }

  @Test
  fun `Save does nothing before the screen is ready`() = runTest(testDispatcher) {
    val viewModel = SelectFeaturedBadgeViewModel()

    viewModel.onEvent(SelectFeaturedBadgeEvents.BadgeSelected(first))
    viewModel.onEvent(SelectFeaturedBadgeEvents.Save)

    assertThat(viewModel.state.value.stage).isEqualTo(SelectFeaturedBadgeState.Stage.INIT)
    coVerify(exactly = 0) { BadgeRepository.setFeaturedBadge(any()) }
  }

  @Test
  fun `a second Save after success is ignored`() = runTest(testDispatcher) {
    val viewModel = createViewModel(badges = listOf(first))

    viewModel.onEvent(SelectFeaturedBadgeEvents.Save)
    viewModel.onEvent(SelectFeaturedBadgeEvents.Save)

    coVerify(exactly = 1) { BadgeRepository.setFeaturedBadge(first) }
  }

  private fun createViewModel(badges: List<Badge>): SelectFeaturedBadgeViewModel {
    val viewModel = SelectFeaturedBadgeViewModel()
    selfSubject.onNext(createSelf(badges))
    return viewModel
  }

  private fun TestScope.collectActions(actions: Flow<SelectFeaturedBadgeActions>): List<SelectFeaturedBadgeActions> {
    val collected = mutableListOf<SelectFeaturedBadgeActions>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
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
