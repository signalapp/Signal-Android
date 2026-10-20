/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.database.deleteAll
import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import org.signal.donations.StripeApi
import org.signal.donations.json.StripeIntentStatus
import org.signal.donations.json.StripePaymentIntent
import org.signal.donations.json.StripeSetupIntent
import org.signal.libsignal.net.RequestResult
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatValue
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.InAppPaymentSubscriberTable
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.internal.EmptyResponse
import org.whispersystems.signalservice.internal.ServiceResponse
import java.math.BigDecimal
import java.net.SocketTimeoutException
import java.util.Currency
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class InAppPaymentAuthCheckJobTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

  @get:Rule
  val signalStore = MockSignalStoreRule()

  @Before
  fun setUp() {
    SignalDatabase.inAppPayments.writableDatabase.deleteAll(InAppPaymentTable.TABLE_NAME)
    SignalDatabase.inAppPaymentSubscribers.writableDatabase.deleteAll(InAppPaymentSubscriberTable.TABLE_NAME)
    every { signalStore.inAppPayments.consumePending3DSData() } returns null
    mockkConstructor(StripeApi::class)
    mockkObject(RemoteConfig)
    every { RemoteConfig.defaultMaxBackoff } returns 60.minutes.inWholeMilliseconds
    mockkObject(RecurringInAppPaymentRepository)
    every { RecurringInAppPaymentRepository.onSubscriptionLevelSet(any()) } returns Unit
  }

  @After
  fun tearDown() {
    unmockkConstructor(StripeApi::class)
    unmockkObject(RemoteConfig)
    unmockkObject(RecurringInAppPaymentRepository)
  }

  @Test
  fun givenRequiresActionPaymentWithoutEnteredAtOlderThanTimeout_whenIRun_thenIExpectPaymentToEndWithError() {
    givenStripeStatus(StripeIntentStatus.REQUIRES_ACTION)
    val id = insertWaitingForAuth(checkedVerification = false, age = 20.minutes)

    InAppPaymentAuthCheckJob().run()

    val payment = SignalDatabase.inAppPayments.getById(id)!!
    assertThat(payment.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(payment.data.error?.type).isEqualTo(InAppPaymentData.Error.Type.PAYMENT_SETUP)
  }

  @Test
  fun givenRequiresActionPaymentWaitingLongerThanTimeout_whenIRun_thenIExpectPaymentToEndWithError() {
    givenStripeStatus(StripeIntentStatus.REQUIRES_ACTION)
    val id = insertWaitingForAuth(checkedVerification = true, age = 20.minutes, waitingFor = 16.minutes)

    InAppPaymentAuthCheckJob().run()

    assertThat(SignalDatabase.inAppPayments.getById(id)!!.state).isEqualTo(InAppPaymentTable.State.END)
  }

  @Test
  fun givenOldRequiresActionPaymentThatRecentlyEnteredWaiting_whenIRun_thenIExpectRetry() {
    givenStripeStatus(StripeIntentStatus.REQUIRES_ACTION)
    val id = insertWaitingForAuth(checkedVerification = false, age = 2.hours, waitingFor = 4.minutes)
    val enteredAt = SignalDatabase.inAppPayments.getById(id)!!.data.waitForAuth!!.enteredAt

    val result = InAppPaymentAuthCheckJob().run()

    assertThat(result.isRetry).isTrue()
    val payment = SignalDatabase.inAppPayments.getById(id)!!
    assertThat(payment.state).isEqualTo(InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION)
    assertThat(payment.data.waitForAuth?.checkedVerification).isEqualTo(true)
    assertThat(payment.data.waitForAuth?.enteredAt).isEqualTo(enteredAt)
  }

  @Test
  fun givenRequiresActionPaymentYoungerThanTimeout_whenIRun_thenIExpectRetry() {
    givenStripeStatus(StripeIntentStatus.REQUIRES_ACTION)
    val id = insertWaitingForAuth(checkedVerification = false, age = 5.minutes)

    val result = InAppPaymentAuthCheckJob().run()

    assertThat(result.isRetry).isTrue()
    val payment = SignalDatabase.inAppPayments.getById(id)!!
    assertThat(payment.state).isEqualTo(InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION)
    assertThat(payment.data.waitForAuth?.checkedVerification).isEqualTo(true)
  }

  @Test
  fun givenNetworkErrorDownloadingPaymentIntent_whenIRun_thenIExpectRetryAndPaymentStillWaiting() {
    every { anyConstructed<StripeApi>().getPaymentIntent(any()) } returns RequestResult.RetryableNetworkError(SocketTimeoutException())
    val id = insertWaitingForAuth(checkedVerification = false, age = 5.minutes)

    val result = InAppPaymentAuthCheckJob().run()

    assertThat(result.isRetry).isTrue()
    val payment = SignalDatabase.inAppPayments.getById(id)!!
    assertThat(payment.state).isEqualTo(InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION)
    assertThat(payment.data.error).isNull()
  }

  // region SEPA / PROCESSING -- settlement can take up to two weeks, so these must never time out.

  @Test
  fun givenProcessingPaymentsOfAnyAge_whenIRun_thenIExpectAllToBePending() {
    givenStripeStatus(StripeIntentStatus.PROCESSING)
    val ids = mapOf(
      "SEPA, younger than timeout" to insertWaitingForAuth(checkedVerification = false, age = 1.minutes, paymentMethodType = InAppPaymentData.PaymentMethodType.SEPA_DEBIT),
      "SEPA, older than timeout" to insertWaitingForAuth(checkedVerification = true, age = 20.minutes, paymentMethodType = InAppPaymentData.PaymentMethodType.SEPA_DEBIT),
      "SEPA, older than lifespan" to insertWaitingForAuth(checkedVerification = true, age = 13.days, paymentMethodType = InAppPaymentData.PaymentMethodType.SEPA_DEBIT),
      "iDEAL, older than timeout" to insertWaitingForAuth(checkedVerification = true, age = 20.minutes, paymentMethodType = InAppPaymentData.PaymentMethodType.IDEAL)
    )

    InAppPaymentAuthCheckJob().run()

    ids.forEach { (case, id) -> assertPendingWithRedemptionStarted(case, id) }
  }

  @Test
  fun givenProcessingRecurringSepaSetupOlderThanTimeout_whenIRun_thenIExpectSubscriptionLevelUpdateBookkeepingAndPending() {
    val subscriberId = insertDonationSubscriber()
    every { anyConstructed<StripeApi>().getSetupIntent(any()) } returns RequestResult.Success(
      StripeSetupIntent(
        id = "test-intent-id",
        clientSecret = "test-client-secret",
        status = StripeIntentStatus.PROCESSING,
        paymentMethodId = "pm_test",
        customer = null
      )
    )
    every { signalStore.inAppPayments.getLevelOperation(any()) } returns null
    every { AppDependencies.donationsService.setDefaultStripePaymentMethod(any(), any()) } returns ServiceResponse.forResult(EmptyResponse.INSTANCE, 200, "")
    every { AppDependencies.donationsService.updateSubscriptionLevel(any(), any(), any(), any(), any()) } returns ServiceResponse.forResult(EmptyResponse.INSTANCE, 200, "")

    val id = insertWaitingForAuth(
      checkedVerification = true,
      age = 20.minutes,
      paymentMethodType = InAppPaymentData.PaymentMethodType.SEPA_DEBIT,
      type = InAppPaymentType.RECURRING_DONATION,
      subscriberId = subscriberId
    )

    InAppPaymentAuthCheckJob().run()

    assertThat(SignalDatabase.inAppPayments.getById(id)!!.state).isEqualTo(InAppPaymentTable.State.PENDING)
    verify { AppDependencies.donationsService.setDefaultStripePaymentMethod(subscriberId, "pm_test") }
    verify { AppDependencies.donationsService.updateSubscriptionLevel(subscriberId, "500", "EUR", any(), any()) }
    verify { RecurringInAppPaymentRepository.onSubscriptionLevelSet(InAppPaymentSubscriberRecord.Type.DONATION) }
  }

  @Test
  fun givenPendingSepaPaymentOlderThanLifespan_whenIFail_thenIExpectPaymentToStillBePending() {
    val id = SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.ONE_TIME_DONATION,
      state = InAppPaymentTable.State.PENDING,
      subscriberId = null,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData(
        amount = FiatMoney(BigDecimal.ONE, Currency.getInstance("EUR")).toFiatValue(),
        paymentMethodType = InAppPaymentData.PaymentMethodType.SEPA_DEBIT,
        redemption = InAppPaymentData.RedemptionState(stage = InAppPaymentData.RedemptionState.Stage.INIT)
      )
    )
    val inserted = SignalDatabase.inAppPayments.getById(id)!!
    SignalDatabase.inAppPayments.update(inserted.copy(insertedAt = System.currentTimeMillis().milliseconds - 13.days))

    InAppPaymentAuthCheckJob().onFailure()

    val payment = SignalDatabase.inAppPayments.getById(id)!!
    assertThat(payment.state).isEqualTo(InAppPaymentTable.State.PENDING)
    assertThat(payment.data.error).isNull()
  }

  // endregion

  @Test
  fun givenSucceededPaymentOlderThanTimeout_whenIRun_thenIExpectPaymentToBePending() {
    givenStripeStatus(StripeIntentStatus.SUCCEEDED)
    val id = insertWaitingForAuth(checkedVerification = true, age = 20.minutes)

    InAppPaymentAuthCheckJob().run()

    assertThat(SignalDatabase.inAppPayments.getById(id)!!.state).isEqualTo(InAppPaymentTable.State.PENDING)
  }

  @Test
  fun givenCheckedPaymentOlderThanLifespan_whenIFail_thenIExpectPaymentToEndWithError() {
    val id = insertWaitingForAuth(checkedVerification = true, age = 2.days)

    InAppPaymentAuthCheckJob().onFailure()

    val payment = SignalDatabase.inAppPayments.getById(id)!!
    assertThat(payment.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(payment.data.error?.type).isEqualTo(InAppPaymentData.Error.Type.PAYMENT_SETUP)
  }

  @Test
  fun givenCheckedPaymentYoungerThanLifespan_whenIFail_thenIExpectPaymentToStillBeWaiting() {
    val id = insertWaitingForAuth(checkedVerification = true, age = 1.hours)

    InAppPaymentAuthCheckJob().onFailure()

    val payment = SignalDatabase.inAppPayments.getById(id)!!
    assertThat(payment.state).isEqualTo(InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION)
    assertThat(payment.data.error).isNull()
  }

  @Test
  fun givenUncheckedPaymentOlderThanLifespan_whenIFail_thenIExpectPaymentToStillBeWaiting() {
    val id = insertWaitingForAuth(checkedVerification = false, age = 2.days)

    InAppPaymentAuthCheckJob().onFailure()

    val payment = SignalDatabase.inAppPayments.getById(id)!!
    assertThat(payment.state).isEqualTo(InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION)
    assertThat(payment.data.error).isNull()
  }

  private fun givenStripeStatus(status: StripeIntentStatus) {
    every { anyConstructed<StripeApi>().getPaymentIntent(any()) } returns RequestResult.Success(
      StripePaymentIntent(
        id = "test-intent-id",
        clientSecret = "test-client-secret",
        status = status,
        paymentMethod = null
      )
    )
  }

  private fun assertPendingWithRedemptionStarted(case: String, id: InAppPaymentTable.InAppPaymentId) {
    val payment = SignalDatabase.inAppPayments.getById(id)!!
    assertThat(payment.state, name = "$case: state").isEqualTo(InAppPaymentTable.State.PENDING)
    assertThat(payment.data.error, name = "$case: error").isNull()
    assertThat(payment.data.redemption?.stage, name = "$case: redemption stage").isEqualTo(InAppPaymentData.RedemptionState.Stage.INIT)
  }

  private fun insertDonationSubscriber(): SubscriberId {
    val subscriberId = SubscriberId.generate()
    every { signalStore.inAppPayments.getRecurringDonationCurrency() } returns Currency.getInstance("EUR")
    SignalDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        subscriberId = subscriberId,
        currency = Currency.getInstance("EUR"),
        type = InAppPaymentSubscriberRecord.Type.DONATION,
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.SEPA_DEBIT,
        iapSubscriptionId = null
      )
    )
    return subscriberId
  }

  private fun insertWaitingForAuth(
    checkedVerification: Boolean,
    age: Duration,
    paymentMethodType: InAppPaymentData.PaymentMethodType = InAppPaymentData.PaymentMethodType.IDEAL,
    type: InAppPaymentType = InAppPaymentType.ONE_TIME_DONATION,
    subscriberId: SubscriberId? = null,
    waitingFor: Duration? = null
  ): InAppPaymentTable.InAppPaymentId {
    val id = SignalDatabase.inAppPayments.insert(
      type = type,
      state = InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION,
      subscriberId = subscriberId,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData(
        amount = FiatMoney(BigDecimal.ONE, Currency.getInstance("EUR")).toFiatValue(),
        level = 500L,
        paymentMethodType = paymentMethodType,
        waitForAuth = InAppPaymentData.WaitingForAuthorizationState(
          stripeIntentId = "test-intent-id",
          stripeClientSecret = "test-client-secret",
          checkedVerification = checkedVerification,
          enteredAt = waitingFor?.let { (System.currentTimeMillis().milliseconds - it).inWholeMilliseconds } ?: 0
        )
      )
    )

    val inserted = SignalDatabase.inAppPayments.getById(id)!!
    SignalDatabase.inAppPayments.update(inserted.copy(insertedAt = System.currentTimeMillis().milliseconds - age))

    return id
  }
}
