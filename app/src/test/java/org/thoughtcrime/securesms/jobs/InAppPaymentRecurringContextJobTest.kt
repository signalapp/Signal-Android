package org.thoughtcrime.securesms.jobs

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsTestRule
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class InAppPaymentRecurringContextJobTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val inAppPaymentsTestRule = InAppPaymentsTestRule()

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())

    mockkObject(InAppPaymentsRepository)
    every { InAppPaymentsRepository.generateRequestCredential() } returns mockk {
      every { serialize() } returns byteArrayOf()
      every { request } returns mockk()
    }

    inAppPaymentsTestRule.initializeSubmitReceiptCredentialRequestSync()

    val minimumTime = System.currentTimeMillis().milliseconds + 60.days
    val minimumTimeS = minimumTime.inWholeSeconds
    val offset = minimumTimeS % 86400L
    val actualMinimumTime = minimumTimeS - offset

    every { AppDependencies.clientZkReceiptOperations.receiveReceiptCredential(any(), any()) } returns mockk() {
      every { receiptLevel } returns 2000
      every { receiptExpirationTime } returns actualMinimumTime
    }
  }

  @Test
  fun `Given a CREATED IAP, when I onAdded, then I expect PENDING`() {
    val iap = insertInAppPayment()
    val job = InAppPaymentRecurringContextJob.create(iap)

    job.onAdded()
    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)

    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.PENDING)
  }

  @Test
  fun `Given an IAP without an error, when I onFailure, then I set error and move to end state`() {
    val iap = insertInAppPayment()
    val job = InAppPaymentRecurringContextJob.create(iap)

    job.onFailure()
    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)

    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(updatedIap?.data?.error).isNotNull()
  }

  @Test
  fun `Given a SEPA IAP, when I getNextRunAttemptBackoff, then I expect one day`() {
    val iap = insertInAppPayment(paymentSourceType = PaymentSourceType.Stripe.SEPADebit)
    val job = InAppPaymentRecurringContextJob.create(iap)

    val result = job.getNextRunAttemptBackoff(1, Exception())

    assertThat(result).isEqualTo(1.days.inWholeMilliseconds)
  }

  @Test
  fun `Given an iDEAL IAP, when I getNextRunAttemptBackoff, then I expect one day`() {
    val iap = insertInAppPayment(paymentSourceType = PaymentSourceType.Stripe.IDEAL)
    val job = InAppPaymentRecurringContextJob.create(iap)

    val result = job.getNextRunAttemptBackoff(1, Exception())

    assertThat(result).isEqualTo(1.days.inWholeMilliseconds)
  }

  @Test
  fun `Test happy path for subscription redemption`() {
    val activeSubscription = inAppPaymentsTestRule.createActiveSubscription()
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription)

    val iap = insertInAppPayment(paymentSourceType = PaymentSourceType.Stripe.IDEAL)
    val job = InAppPaymentRecurringContextJob.create(iap)
    job.onAdded()

    val result = job.run()
    assertThat(result.isSuccess).isTrue()
  }

  @Test
  fun `Given END state, when I run, then I expect failure`() {
    val iap = insertInAppPayment(
      state = InAppPaymentTable.State.END
    )

    val job = InAppPaymentRecurringContextJob.create(iap)

    val result = job.run()
    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given non-recurring IAP, when I run, then I expect failure`() {
    val iap = insertInAppPayment(
      type = InAppPaymentType.ONE_TIME_GIFT
    )

    val job = InAppPaymentRecurringContextJob.create(iap)

    val result = job.run()
    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given no subscriber id, when I run, then I expect failure`() {
    val iap = insertInAppPayment(
      subscriberId = null
    )

    val job = InAppPaymentRecurringContextJob.create(iap)

    val result = job.run()
    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given no redemption data, when I run, then I expect failure`() {
    val iap = insertInAppPayment(
      redemptionState = null
    )

    val job = InAppPaymentRecurringContextJob.create(iap)

    val result = job.run()
    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given redemption started, when I run, then I expect failure`() {
    val iap = insertInAppPayment(
      redemptionState = InAppPaymentData.RedemptionState(
        stage = InAppPaymentData.RedemptionState.Stage.REDEMPTION_STARTED
      )
    )

    val job = InAppPaymentRecurringContextJob.create(iap)

    val result = job.run()
    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given redeemed, when I run, then I expect failure`() {
    val iap = insertInAppPayment(
      redemptionState = InAppPaymentData.RedemptionState(
        stage = InAppPaymentData.RedemptionState.Stage.REDEEMED
      )
    )

    val job = InAppPaymentRecurringContextJob.create(iap)

    val result = job.run()
    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given no available subscription, when I run, then I expect retry`() {
    val iap = insertInAppPayment()
    val job = InAppPaymentRecurringContextJob.create(iap)
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(ActiveSubscription(null, null))

    val result = job.run()
    assertThat(result.isRetry).isTrue()
  }

  private fun insertInAppPayment(
    type: InAppPaymentType = InAppPaymentType.RECURRING_DONATION,
    state: InAppPaymentTable.State = InAppPaymentTable.State.CREATED,
    subscriberId: SubscriberId? = SubscriberId.generate(),
    paymentSourceType: PaymentSourceType = PaymentSourceType.Stripe.CreditCard,
    redemptionState: InAppPaymentData.RedemptionState? = InAppPaymentData.RedemptionState(
      stage = InAppPaymentData.RedemptionState.Stage.INIT
    )
  ): InAppPaymentTable.InAppPayment {
    val iap = inAppPaymentsTestRule.createInAppPayment(type, paymentSourceType)
    SignalDatabase.inAppPayments.insert(
      type = iap.type,
      state = state,
      subscriberId = subscriberId,
      endOfPeriod = null,
      inAppPaymentData = iap.data.copy(
        redemption = redemptionState
      )
    )

    return iap
  }
}
