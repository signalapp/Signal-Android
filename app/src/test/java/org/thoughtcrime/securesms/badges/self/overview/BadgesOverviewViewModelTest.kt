/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.overview

import android.net.Uri
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.badges.BadgesOverviewEvent
import org.signal.appsettings.badges.BadgesOverviewState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.models.badges.Badge
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class BadgesOverviewViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()
  private val activeBadge = badge(id = "star", expirationTimestamp = Long.MAX_VALUE)
  private val expiredBadge = badge(id = "boost", expirationTimestamp = 1)
  private val self = MutableStateFlow(recipient(badges = listOf(activeBadge, expiredBadge), featuredBadge = activeBadge))
  private val internet = MutableStateFlow(true)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(BadgesOverviewRepository)

    every { BadgesOverviewRepository.observeSelf() } returns self
    every { BadgesOverviewRepository.observeInternetConnection() } returns internet
    every { BadgesOverviewRepository.getDisplayBadgesOnProfile() } returns true
    coEvery { BadgesOverviewRepository.getFadedBadgeId() } returns null
    coEvery { BadgesOverviewRepository.setDisplayBadgesOnProfile(any()) } returns true
  }

  @After
  fun tearDown() {
    unmockkObject(BadgesOverviewRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `the badges are read from the self recipient`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    val state = viewModel.state.value
    assertThat(state.stage).isEqualTo(BadgesOverviewState.Stage.READY)
    assertThat(state.badges).containsExactly(activeBadge, expiredBadge)
    assertThat(state.featuredBadge).isEqualTo(activeBadge)
    assertThat(state.displayBadgesOnProfile).isTrue()
    assertThat(state.hasUnexpiredBadges).isTrue()
    assertThat(state.canChangeSettings).isTrue()
  }

  @Test
  fun `settings can't be changed when every badge has expired`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    self.value = recipient(badges = listOf(expiredBadge), featuredBadge = null)

    assertThat(viewModel.state.value.hasUnexpiredBadges).isFalse()
    assertThat(viewModel.state.value.canChangeSettings).isFalse()
  }

  @Test
  fun `settings can't be changed while offline`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    internet.value = false

    assertThat(viewModel.state.value.canChangeSettings).isFalse()
  }

  @Test
  fun `a cancelled donation's badge is faded`() = runTest(testDispatcher) {
    coEvery { BadgesOverviewRepository.getFadedBadgeId() } returns activeBadge.id

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.fadedBadgeId).isEqualTo(activeBadge.id)
  }

  @Test
  fun `clicking an active badge opens its sheet until it's dismissed`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(BadgesOverviewEvent.BadgeClicked(activeBadge))

    assertThat(viewModel.state.value.viewedBadge).isEqualTo(activeBadge)
    assertThat(viewModel.state.value.expiredBadge).isNull()

    viewModel.onEvent(BadgesOverviewEvent.BadgeSheetDismissed)

    assertThat(viewModel.state.value.viewedBadge).isNull()
  }

  @Test
  fun `clicking an expired badge explains that it expired until the sheet is dismissed`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(BadgesOverviewEvent.BadgeClicked(expiredBadge))

    assertThat(viewModel.state.value.expiredBadge).isEqualTo(expiredBadge)
    assertThat(viewModel.state.value.viewedBadge).isNull()

    viewModel.onEvent(BadgesOverviewEvent.ExpiredBadgeSheetDismissed)

    assertThat(viewModel.state.value.expiredBadge).isNull()
  }

  @Test
  fun `clicking a faded badge explains that it will expire`() = runTest(testDispatcher) {
    coEvery { BadgesOverviewRepository.getFadedBadgeId() } returns activeBadge.id
    val viewModel = createViewModel()

    viewModel.onEvent(BadgesOverviewEvent.BadgeClicked(activeBadge))

    assertThat(viewModel.state.value.expiredBadge).isEqualTo(activeBadge)
  }

  @Test
  fun `toggling display badges saves it and shows progress until it's done`() = runTest(testDispatcher) {
    val saved = CompletableDeferred<Boolean>()
    coEvery { BadgesOverviewRepository.setDisplayBadgesOnProfile(false) } coAnswers { saved.await() }
    val viewModel = createViewModel()

    viewModel.onEvent(BadgesOverviewEvent.DisplayBadgesOnProfileToggled(false))
    assertThat(viewModel.state.value.stage).isEqualTo(BadgesOverviewState.Stage.UPDATING_BADGE_DISPLAY_STATE)

    saved.complete(true)

    coVerify { BadgesOverviewRepository.setDisplayBadgesOnProfile(false) }
    assertThat(viewModel.state.value.stage).isEqualTo(BadgesOverviewState.Stage.READY)
    assertThat(viewModel.state.value.snackbar).isEqualTo(BadgesOverviewState.Snackbar.None)
  }

  @Test
  fun `navigating back still works while display badges is saving`() = runTest(testDispatcher) {
    coEvery { BadgesOverviewRepository.setDisplayBadgesOnProfile(any()) } coAnswers { CompletableDeferred<Boolean>().await() }
    val viewModel = createViewModel()

    viewModel.onEvent(BadgesOverviewEvent.DisplayBadgesOnProfileToggled(false))
    viewModel.onEvent(BadgesOverviewEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `a failed display badges save shows a snackbar until it's dismissed`() = runTest(testDispatcher) {
    coEvery { BadgesOverviewRepository.setDisplayBadgesOnProfile(any()) } returns false
    val viewModel = createViewModel()

    viewModel.onEvent(BadgesOverviewEvent.DisplayBadgesOnProfileToggled(false))

    assertThat(viewModel.state.value.stage).isEqualTo(BadgesOverviewState.Stage.READY)
    assertThat(viewModel.state.value.snackbar).isEqualTo(BadgesOverviewState.Snackbar.FailedToUpdateProfile)

    viewModel.onEvent(BadgesOverviewEvent.SnackbarDismissed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(BadgesOverviewState.Snackbar.None)
  }

  @Test
  fun `FeaturedBadgeClicked opens the featured badge picker`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(BadgesOverviewEvent.FeaturedBadgeClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.DonationsRoute.Featured))
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(BadgesOverviewEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  private fun createViewModel(): BadgesOverviewViewModel = BadgesOverviewViewModel(parentEventEmitter = { navigationEvents += it })

  private fun badge(id: String, expirationTimestamp: Long): Badge {
    return Badge(
      id = id,
      category = Badge.Category.Donor,
      name = id,
      description = "",
      imageUrl = mockk<Uri>(),
      imageDensity = "xxxhdpi",
      expirationTimestamp = expirationTimestamp,
      visible = true,
      duration = 0
    )
  }

  private fun recipient(badges: List<Badge>, featuredBadge: Badge?): Recipient {
    return mockk {
      every { this@mockk.badges } returns badges
      every { this@mockk.featuredBadge } returns featuredBadge
    }
  }
}
