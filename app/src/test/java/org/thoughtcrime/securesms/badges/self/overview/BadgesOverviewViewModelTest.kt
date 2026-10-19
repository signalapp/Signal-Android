/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.overview

import android.app.Application
import android.net.Uri
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.CompletableDeferred
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
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.subscription.Subscription
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.util.InternetConnectionObserver
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BadgesOverviewViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val signalStore = MockSignalStoreRule()

  private val selfSubject = BehaviorSubject.create<Recipient>()
  private val connectivitySubject = BehaviorSubject.create<Boolean>()

  private val first = createBadge("first")
  private val second = createBadge("second")

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    mockkObject(Recipient.Companion)
    every { Recipient.self() } returns createSelf(emptyList())
    every { Recipient.observable(SELF_ID) } returns selfSubject

    mockkObject(InternetConnectionObserver)
    every { InternetConnectionObserver.observe() } returns connectivitySubject

    mockkObject(RecurringInAppPaymentRepository)
    every { RecurringInAppPaymentRepository.getActiveSubscription(InAppPaymentSubscriberRecord.Type.DONATION) } returns Single.just(ActiveSubscription.EMPTY)
    every { RecurringInAppPaymentRepository.getSubscriptions() } returns Single.just(emptyList())

    mockkObject(BadgeRepository)
    coEvery { BadgeRepository.setVisibilityForAllBadges(any(), any()) } returns NetworkResult.Success(Unit)

    every { signalStore.inAppPayments.getDisplayBadgesOnProfile() } returns true
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  @Test
  fun `initial state reflects the self recipient`() = runTest(testDispatcher) {
    val viewModel = createViewModel(badges = listOf(first, second))

    val state = viewModel.state.value
    assertThat(state.stage).isEqualTo(BadgesOverviewState.Stage.READY)
    assertThat(state.allUnlockedBadges).containsExactly(first, second)
    assertThat(state.featuredBadge).isEqualTo(first)
    assertThat(state.displayBadgesOnProfile).isTrue()
  }

  @Test
  fun `an unresolved self recipient is ignored`() = runTest(testDispatcher) {
    val viewModel = BadgesOverviewViewModel()

    selfSubject.onNext(createSelf(listOf(first), isResolving = true))

    val state = viewModel.state.value
    assertThat(state.stage).isEqualTo(BadgesOverviewState.Stage.INIT)
    assertThat(state.allUnlockedBadges).isEmpty()
  }

  @Test
  fun `hasInternet follows the connection observer`() = runTest(testDispatcher) {
    val viewModel = createViewModel(badges = listOf(first))

    connectivitySubject.onNext(true)
    assertThat(viewModel.state.value.hasInternet).isTrue()

    connectivitySubject.onNext(false)
    assertThat(viewModel.state.value.hasInternet).isFalse()
  }

  @Test
  fun `a subscription cancelling at period end fades its badge`() = runTest(testDispatcher) {
    every { RecurringInAppPaymentRepository.getActiveSubscription(InAppPaymentSubscriberRecord.Type.DONATION) } returns Single.just(createActiveSubscription(isActive = false, willCancelAtPeriodEnd = true))
    every { RecurringInAppPaymentRepository.getSubscriptions() } returns Single.just(listOf(createSubscription(first)))

    val viewModel = createViewModel(badges = listOf(first))

    assertThat(viewModel.state.value.fadedBadgeId).isEqualTo(first.id)
  }

  @Test
  fun `an active subscription does not fade its badge`() = runTest(testDispatcher) {
    every { RecurringInAppPaymentRepository.getActiveSubscription(InAppPaymentSubscriberRecord.Type.DONATION) } returns Single.just(createActiveSubscription(isActive = true, willCancelAtPeriodEnd = true))
    every { RecurringInAppPaymentRepository.getSubscriptions() } returns Single.just(listOf(createSubscription(first)))

    val viewModel = createViewModel(badges = listOf(first))

    assertThat(viewModel.state.value.fadedBadgeId).isNull()
  }

  @Test
  fun `a failed subscription lookup leaves no badge faded`() = runTest(testDispatcher) {
    every { RecurringInAppPaymentRepository.getSubscriptions() } returns Single.error(IOException())

    val viewModel = createViewModel(badges = listOf(first))

    assertThat(viewModel.state.value.fadedBadgeId).isNull()
    assertThat(viewModel.state.value.stage).isEqualTo(BadgesOverviewState.Stage.READY)
  }

  @Test
  fun `setDisplayBadgesOnProfile shows progress until the update finishes`() = runTest(testDispatcher) {
    val gate = CompletableDeferred<NetworkResult<Unit>>()
    coEvery { BadgeRepository.setVisibilityForAllBadges(false, any()) } coAnswers { gate.await() }

    val viewModel = createViewModel(badges = listOf(first))

    viewModel.setDisplayBadgesOnProfile(false)
    assertThat(viewModel.state.value.stage).isEqualTo(BadgesOverviewState.Stage.UPDATING_BADGE_DISPLAY_STATE)

    gate.complete(NetworkResult.Success(Unit))
    assertThat(viewModel.state.value.stage).isEqualTo(BadgesOverviewState.Stage.READY)
    coVerify(exactly = 1) { BadgeRepository.setVisibilityForAllBadges(false, any()) }
  }

  @Test
  fun `a failed setDisplayBadgesOnProfile returns to READY and tells the user`() = runTest(testDispatcher) {
    coEvery { BadgeRepository.setVisibilityForAllBadges(any(), any()) } returns NetworkResult.NetworkError(IOException())

    val viewModel = createViewModel(badges = listOf(first))
    val events = collectEvents(viewModel.events)

    viewModel.setDisplayBadgesOnProfile(false)

    assertThat(viewModel.state.value.stage).isEqualTo(BadgesOverviewState.Stage.READY)
    assertThat(events).containsExactly(BadgesOverviewEvent.FAILED_TO_UPDATE_PROFILE)
  }

  private fun createViewModel(badges: List<Badge>): BadgesOverviewViewModel {
    val viewModel = BadgesOverviewViewModel()
    selfSubject.onNext(createSelf(badges))
    return viewModel
  }

  private fun TestScope.collectEvents(events: Flow<BadgesOverviewEvent>): List<BadgesOverviewEvent> {
    val collected = mutableListOf<BadgesOverviewEvent>()
    backgroundScope.launch { events.toList(collected) }
    return collected
  }

  private fun createSelf(badges: List<Badge>, isResolving: Boolean = false): Recipient {
    return Recipient(id = SELF_ID, isResolving = isResolving, badges = badges)
  }

  private fun createActiveSubscription(isActive: Boolean, willCancelAtPeriodEnd: Boolean): ActiveSubscription {
    return ActiveSubscription(
      activeSubscription = ActiveSubscription.Subscription(
        level = LEVEL,
        isActive = isActive,
        willCancelAtPeriodEnd = willCancelAtPeriodEnd
      )
    )
  }

  private fun createSubscription(badge: Badge): Subscription {
    return Subscription(id = LEVEL.toString(), badge = badge, prices = emptySet(), level = LEVEL)
  }

  private fun createBadge(id: String): Badge {
    return Badge(
      id = id,
      category = Badge.Category.Donor,
      name = id,
      description = "",
      imageUrl = Uri.EMPTY,
      imageDensity = "xxxhdpi",
      expirationTimestamp = 0,
      visible = true,
      duration = 0
    )
  }

  companion object {
    private val SELF_ID = RecipientId.from(1)
    private const val LEVEL = 500
  }
}
