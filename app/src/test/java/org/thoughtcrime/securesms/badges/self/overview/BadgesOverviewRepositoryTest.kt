/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.overview

import android.app.Application
import android.net.Uri
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BadgesOverviewRepositoryTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val signalStore = MockSignalStoreRule()

  private val selfSubject = BehaviorSubject.create<Recipient>()
  private val badge = createBadge("first")

  @Before
  fun setUp() {
    mockkObject(Recipient.Companion)
    every { Recipient.self() } returns createSelf(emptyList())
    every { Recipient.observable(SELF_ID) } returns selfSubject

    mockkObject(RecurringInAppPaymentRepository)
    every { RecurringInAppPaymentRepository.getActiveSubscription(InAppPaymentSubscriberRecord.Type.DONATION) } returns Single.just(ActiveSubscription.EMPTY)
    every { RecurringInAppPaymentRepository.getSubscriptions() } returns Single.just(emptyList())

    mockkObject(BadgeRepository)
    coEvery { BadgeRepository.setVisibilityForAllBadges(any(), any()) } returns NetworkResult.Success(Unit)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `a self recipient that's still resolving is skipped`() = runTest(UnconfinedTestDispatcher()) {
    val emitted = mutableListOf<Recipient>()
    backgroundScope.launch { BadgesOverviewRepository.observeSelf().toList(emitted) }

    selfSubject.onNext(createSelf(listOf(badge), isResolving = true))
    selfSubject.onNext(createSelf(listOf(badge)))

    assertThat(emitted.map { it.isResolving }).containsExactly(false)
  }

  @Test
  fun `a subscription cancelling at period end fades its badge`() = runTest {
    every { RecurringInAppPaymentRepository.getActiveSubscription(InAppPaymentSubscriberRecord.Type.DONATION) } returns Single.just(createActiveSubscription(isActive = false, willCancelAtPeriodEnd = true))
    every { RecurringInAppPaymentRepository.getSubscriptions() } returns Single.just(listOf(createSubscription(badge)))

    assertThat(BadgesOverviewRepository.getFadedBadgeId()).isEqualTo(badge.id)
  }

  @Test
  fun `an active subscription does not fade its badge`() = runTest {
    every { RecurringInAppPaymentRepository.getActiveSubscription(InAppPaymentSubscriberRecord.Type.DONATION) } returns Single.just(createActiveSubscription(isActive = true, willCancelAtPeriodEnd = true))
    every { RecurringInAppPaymentRepository.getSubscriptions() } returns Single.just(listOf(createSubscription(badge)))

    assertThat(BadgesOverviewRepository.getFadedBadgeId()).isNull()
  }

  @Test
  fun `a failed subscription lookup leaves no badge faded`() = runTest {
    every { RecurringInAppPaymentRepository.getSubscriptions() } returns Single.error(IOException())

    assertThat(BadgesOverviewRepository.getFadedBadgeId()).isNull()
  }

  @Test
  fun `setDisplayBadgesOnProfile reports whether the service took the change`() = runTest {
    assertThat(BadgesOverviewRepository.setDisplayBadgesOnProfile(false)).isTrue()

    coEvery { BadgeRepository.setVisibilityForAllBadges(any(), any()) } returns NetworkResult.NetworkError(IOException())

    assertThat(BadgesOverviewRepository.setDisplayBadgesOnProfile(false)).isFalse()
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
