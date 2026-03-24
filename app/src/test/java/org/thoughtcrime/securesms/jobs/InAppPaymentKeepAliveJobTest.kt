/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import assertk.assertThat
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsTestRule
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription.ChargeFailure
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.internal.EmptyResponse
import org.whispersystems.signalservice.internal.ServiceResponse

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class InAppPaymentKeepAliveJobTest {

  @get:Rule
  val mockSignalStore = MockSignalStoreRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val inAppPaymentsTestRule = InAppPaymentsTestRule()

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())

    every { mockSignalStore.account.isRegistered } returns true
    every { mockSignalStore.account.isLinkedDevice } returns false

    every { SignalDatabase.inAppPayments.getOldPendingPayments(any()) } returns emptyList()
    every { SignalDatabase.inAppPayments.hasPrePendingRecurringTransaction(any()) } returns false

    val subscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = java.util.Currency.getInstance("USD"),
      type = InAppPaymentSubscriberRecord.Type.DONATION,
      requiresCancel = false,
      paymentMethodType = org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData.PaymentMethodType.CARD,
      iapSubscriptionId = null
    )
    every { InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.DONATION) } returns subscriber

    every { AppDependencies.donationsService.putSubscription(any()) } returns ServiceResponse(200, "", EmptyResponse.INSTANCE, null, null)
  }

  @Test
  fun `Given an unregistered local user, when I run, then I expect skip`() {
    every { mockSignalStore.account.isRegistered } returns false

    val job = InAppPaymentKeepAliveJob.create(InAppPaymentSubscriberRecord.Type.DONATION)

    val result = job.run()

    assertThat(result.isSuccess).isTrue()
  }

  @Test
  fun `Given a canceled subscription, when I run, then I write cancellation and return early`() {
    val activeSubscription = inAppPaymentsTestRule.createActiveSubscription(
      status = "canceled",
      isActive = false
    )
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = activeSubscription)
    every { InAppPaymentsRepository.updateInAppPaymentWithCancelation(any(), any()) } returns Unit

    val job = InAppPaymentKeepAliveJob.create(InAppPaymentSubscriberRecord.Type.DONATION)
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    verify(exactly = 1) { InAppPaymentsRepository.updateInAppPaymentWithCancelation(activeSubscription, InAppPaymentSubscriberRecord.Type.DONATION) }
  }

  @Test
  fun `Given a past-due subscription with charge failure, when I run, then I do not write cancellation`() {
    val chargeFailure = ChargeFailure("test", "", "", "", "")
    val activeSubscription = inAppPaymentsTestRule.createActiveSubscription(
      status = "past_due",
      isActive = false,
      chargeFailure = chargeFailure
    )
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = activeSubscription)
    every { InAppPaymentsRepository.updateInAppPaymentWithCancelation(any(), any()) } returns Unit

    val job = InAppPaymentKeepAliveJob.create(InAppPaymentSubscriberRecord.Type.DONATION)
    job.run()

    verify(exactly = 0) { InAppPaymentsRepository.updateInAppPaymentWithCancelation(any(), any()) }
  }
}
