/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSource
import org.signal.donations.PaymentSourceType
import org.signal.libsignal.net.RequestResult
import org.signal.network.rest.RestStatusCodeError
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsTestRule
import org.thoughtcrime.securesms.components.settings.app.subscription.StripeRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class InAppPaymentStripeSetupJobTest {

  @get:Rule
  val mockSignalStore = MockSignalStoreRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val inAppPaymentsTestRule = InAppPaymentsTestRule()

  private val paymentSource: PaymentSource = mockk {
    every { type } returns PaymentSourceType.Stripe.GooglePay
  }

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())

    mockkObject(StripeRepository)

    every { SignalDatabase.inAppPayments.moveToTransacting(any()) } answers {
      val iap = SignalDatabase.inAppPayments.getById(firstArg())!!.copy(state = InAppPaymentTable.State.TRANSACTING)
      SignalDatabase.inAppPayments.update(iap)
      iap
    }
    every { SignalDatabase.inAppPayments.hasWaitingForAuth() } returns true
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `Given a network error reading back a confirmed one-time intent, when I run, then I expect the payment to wait for authorization`() {
    every { StripeRepository.getStatusAndPaymentMethodId(any(), any()) } returns RequestResult.RetryableNetworkError(SocketTimeoutException())
    val iap = insertRequiredActionCompleted(InAppPaymentType.ONE_TIME_DONATION)

    val result = InAppPaymentStripeOneTimeSetupJob.create(iap, paymentSource).run()

    assertThat(result.isSuccess).isTrue()
    val updated = SignalDatabase.inAppPayments.getById(iap.id)!!
    assertThat(updated.state).isEqualTo(InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION)
    assertThat(updated.data.waitForAuth?.stripeIntentId).isEqualTo("pi_test")
    assertThat(updated.data.waitForAuth?.stripeClientSecret).isEqualTo("pi_test_secret")
    assertThat(updated.data.error).isNull()
    verify { AppDependencies.jobManager.add(any<InAppPaymentAuthCheckJob>()) }
  }

  @Test
  fun `Given an application error reading back a confirmed one-time intent, when I run, then I expect the payment to end with an error`() {
    every { StripeRepository.getStatusAndPaymentMethodId(any(), any()) } returns RequestResult.ApplicationError(RuntimeException())
    val iap = insertRequiredActionCompleted(InAppPaymentType.ONE_TIME_DONATION)

    val result = InAppPaymentStripeOneTimeSetupJob.create(iap, paymentSource).run()

    assertThat(result.isFailure).isTrue()
    val updated = SignalDatabase.inAppPayments.getById(iap.id)!!
    assertThat(updated.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(updated.data.error).isNotNull()
  }

  @Test
  fun `Given a 404 reading back a confirmed one-time intent, when I run, then I expect the payment to end with an error`() {
    every { StripeRepository.getStatusAndPaymentMethodId(any(), any()) } returns RequestResult.NonSuccess(RestStatusCodeError(404, emptyMap(), null))
    val iap = insertRequiredActionCompleted(InAppPaymentType.ONE_TIME_DONATION)

    val result = InAppPaymentStripeOneTimeSetupJob.create(iap, paymentSource).run()

    assertThat(result.isFailure).isTrue()
    val updated = SignalDatabase.inAppPayments.getById(iap.id)!!
    assertThat(updated.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(updated.data.error).isNotNull()
  }

  @Test
  fun `Given a network error reading back a confirmed recurring intent, when I run, then I expect the payment to wait for authorization`() {
    val subscriberId = SubscriberId.generate()
    InAppPaymentsTestRule.mockLocalSubscriberAccess(
      InAppPaymentSubscriberRecord(
        subscriberId = subscriberId,
        currency = null,
        type = InAppPaymentSubscriberRecord.Type.DONATION,
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PAY,
        iapSubscriptionId = null
      )
    )
    every { StripeRepository.getStatusAndPaymentMethodId(any(), any()) } returns RequestResult.RetryableNetworkError(SocketTimeoutException())
    val iap = insertRequiredActionCompleted(InAppPaymentType.RECURRING_DONATION)

    val result = InAppPaymentStripeRecurringSetupJob.create(iap, paymentSource).run()

    assertThat(result.isSuccess).isTrue()
    val updated = SignalDatabase.inAppPayments.getById(iap.id)!!
    assertThat(updated.state).isEqualTo(InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION)
    assertThat(updated.subscriberId).isEqualTo(subscriberId)
    assertThat(updated.data.waitForAuth?.stripeIntentId).isEqualTo("pi_test")
    verify { AppDependencies.jobManager.add(any<InAppPaymentAuthCheckJob>()) }
  }

  private fun insertRequiredActionCompleted(type: InAppPaymentType): InAppPaymentTable.InAppPayment {
    val iap = inAppPaymentsTestRule.createInAppPayment(type, PaymentSourceType.Stripe.GooglePay)
    val id = SignalDatabase.inAppPayments.insert(
      type = iap.type,
      state = InAppPaymentTable.State.REQUIRED_ACTION_COMPLETED,
      subscriberId = null,
      endOfPeriod = null,
      inAppPaymentData = iap.data.newBuilder()
        .stripeActionComplete(
          InAppPaymentData.StripeActionCompleteState(
            stripeIntentId = "pi_test",
            stripeClientSecret = "pi_test_secret",
            paymentMethodId = "pm_test"
          )
        )
        .build()
    )

    return SignalDatabase.inAppPayments.getById(id)!!
  }
}
