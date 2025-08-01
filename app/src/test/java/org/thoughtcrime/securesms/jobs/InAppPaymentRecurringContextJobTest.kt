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
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toInAppPaymentDataChargeFailure
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsTestRule
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.BadgeList
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription.ChargeFailure
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.internal.push.WhoAmIResponse
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class InAppPaymentRecurringContextJobTest {

  @get:Rule
  val mockSignalStore = MockSignalStoreRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val inAppPaymentsTestRule = InAppPaymentsTestRule()

  lateinit var recipientTable: RecipientTable

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())

    every { mockSignalStore.account.isRegistered } returns true
    every { mockSignalStore.inAppPayments.setLastEndOfPeriod(any()) } returns Unit

    recipientTable = mockk(relaxed = true)
    every { SignalDatabase.recipients } returns recipientTable

    mockkObject(Recipient)
    every { Recipient.self() } returns Recipient()

    mockkStatic(StorageSyncHelper::class)
    every { StorageSyncHelper.scheduleSyncForDataChange() } returns Unit

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
  fun `Given user is unregistered, when I run then I expect failure`() {
    every { mockSignalStore.account.isRegistered } returns true

    val iap = insertInAppPayment()
    val job = InAppPaymentRecurringContextJob.create(iap)

    val result = job.run()

    assertThat(result.isFailure).isTrue()
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
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = activeSubscription)

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
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = ActiveSubscription(null, null))

    val result = job.run()
    assertThat(result.isRetry).isTrue()
  }

  @Test
  fun `Given getActiveSubscription app-level error, when I run, then I expect failure`() {
    val iap = insertInAppPayment()
    val job = InAppPaymentRecurringContextJob.create(iap)
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(applicationError = Exception())

    val result = job.run()
    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given getActiveSubscription execution error, when I run, then I expect retry`() {
    val iap = insertInAppPayment()
    val job = InAppPaymentRecurringContextJob.create(iap)
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(executionError = Exception())

    val result = job.run()
    assertThat(result.isRetry).isTrue()
  }

  @Test
  fun `Given a failed payment on a keep-alive, when I run, then I expect failure proper iap state`() {
    val iap = insertInAppPayment(
      redemptionState = InAppPaymentData.RedemptionState(
        stage = InAppPaymentData.RedemptionState.Stage.INIT,
        keepAlive = true
      )
    )
    val job = InAppPaymentRecurringContextJob.create(iap)
    val sub = inAppPaymentsTestRule.createActiveSubscription(
      status = "past_due",
      chargeFailure = null
    )

    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = sub)

    val result = job.run()
    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)

    assertThat(updatedIap?.data?.error?.data_).isEqualTo(InAppPaymentKeepAliveJob.KEEP_ALIVE)
    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.PENDING)
    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given a generic failed payment, when I run, then I expect properly updated state`() {
    val iap = insertInAppPayment()
    val job = InAppPaymentRecurringContextJob.create(iap)
    val sub = inAppPaymentsTestRule.createActiveSubscription(
      status = "past_due",
      chargeFailure = null
    )

    InAppPaymentsTestRule.mockLocalSubscriberAccess()
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = sub)

    val result = job.run()

    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)
    assertThat(updatedIap?.data?.error?.data_).isEqualTo("past_due")
    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(result.isFailure).isTrue()

    val subscriber = InAppPaymentsRepository.requireSubscriber(InAppPaymentSubscriberRecord.Type.DONATION)
    assertThat(subscriber.requiresCancel).isTrue()
  }

  @Test
  fun `Given a charge failure, when I run, then I expect properly updated state`() {
    val iap = insertInAppPayment()
    val job = InAppPaymentRecurringContextJob.create(iap)
    val chargeFailure = ChargeFailure("test", "", "", "", "")
    val sub = inAppPaymentsTestRule.createActiveSubscription(
      status = "past_due",
      chargeFailure = chargeFailure
    )

    InAppPaymentsTestRule.mockLocalSubscriberAccess()
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = sub)

    val result = job.run()

    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)
    assertThat(updatedIap?.data?.error?.data_).isEqualTo("test")
    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(result.isFailure).isTrue()

    val subscriber = InAppPaymentsRepository.requireSubscriber(InAppPaymentSubscriberRecord.Type.DONATION)
    assertThat(subscriber.requiresCancel).isTrue()
  }

  @Test
  fun `Given an inactive subscription, when I run, then I retry`() {
    val iap = insertInAppPayment()
    val job = InAppPaymentRecurringContextJob.create(iap)
    val sub = inAppPaymentsTestRule.createActiveSubscription(
      isActive = false,
      chargeFailure = null
    )

    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = sub)

    val result = job.run()
    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)

    assertThat(updatedIap?.data?.error).isNull()
    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.PENDING)
    assertThat(result.isRetry).isTrue()
  }

  @Test
  fun `Given an inactive subscription with a charge failure, when I run, then I update state and fail`() {
    val iap = insertInAppPayment()
    val job = InAppPaymentRecurringContextJob.create(iap)
    val chargeFailure = ChargeFailure("test", "", "", "", "")
    val sub = inAppPaymentsTestRule.createActiveSubscription(
      isActive = false,
      chargeFailure = chargeFailure
    )

    InAppPaymentsTestRule.mockLocalSubscriberAccess()
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = sub)

    val result = job.run()
    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)

    assertThat(updatedIap?.data?.error?.data_).isEqualTo("test")
    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given a canceled subscription with a charge failure for keep-alive, when I run, then I update state and fail`() {
    val iap = insertInAppPayment(
      redemptionState = InAppPaymentData.RedemptionState(
        stage = InAppPaymentData.RedemptionState.Stage.INIT,
        keepAlive = true
      )
    )
    val job = InAppPaymentRecurringContextJob.create(iap)
    val chargeFailure = ChargeFailure("test", "", "", "", "")
    val sub = inAppPaymentsTestRule.createActiveSubscription(
      status = "canceled",
      isActive = false,
      chargeFailure = chargeFailure
    )

    InAppPaymentsTestRule.mockLocalSubscriberAccess()
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = sub)

    val result = job.run()
    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)

    assertThat(updatedIap?.data?.cancellation?.reason).isEqualTo(InAppPaymentData.Cancellation.Reason.CANCELED)
    assertThat(updatedIap?.data?.cancellation?.chargeFailure).isEqualTo(chargeFailure.toInAppPaymentDataChargeFailure())
    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given user has donor entitlement already, when I run, then I do not expect receipt request`() {
    val activeSubscription = inAppPaymentsTestRule.createActiveSubscription()
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = activeSubscription)

    every { AppDependencies.signalServiceAccountManager.whoAmI } returns mockk {
      every { entitlements } returns WhoAmIResponse.Entitlements(
        badges = listOf(WhoAmIResponse.BadgeEntitlement("2000", false, Long.MAX_VALUE))
      )
    }

    val iap = insertInAppPayment(
      badge = BadgeList.Badge(id = "2000")
    )

    val job = InAppPaymentRecurringContextJob.create(iap)
    job.onAdded()

    val result = job.run()
    assertThat(result.isSuccess).isTrue()
    verify(atLeast = 0, atMost = 0) { AppDependencies.donationsService.submitReceiptCredentialRequestSync(any(), any()) }
  }

  @Test
  fun `Given user has backup entitlement already, when I run, then I do not expect receipt request`() {
    val activeSubscription = inAppPaymentsTestRule.createActiveSubscription()
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = activeSubscription)

    every { AppDependencies.signalServiceAccountManager.whoAmI } returns mockk {
      every { entitlements } returns WhoAmIResponse.Entitlements(
        backup = WhoAmIResponse.BackupEntitlement(201L, Long.MAX_VALUE)
      )
    }

    mockkObject(BackupRepository)
    every { BackupRepository.getBackupTier() } returns NetworkResult.Success(MessageBackupTier.PAID)

    val iap = insertInAppPayment(
      type = InAppPaymentType.RECURRING_BACKUP
    )

    val job = InAppPaymentRecurringContextJob.create(iap)
    job.onAdded()

    val result = job.run()
    assertThat(result.isSuccess).isTrue()
    verify(atLeast = 0, atMost = 0) { AppDependencies.donationsService.submitReceiptCredentialRequestSync(any(), any()) }
  }

  @Test
  fun `Given 204 application error, when I run, then I expect a retry`() {
    val activeSubscription = inAppPaymentsTestRule.createActiveSubscription()
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = activeSubscription)
    inAppPaymentsTestRule.initializeSubmitReceiptCredentialRequestSync(status = 204)

    val iap = insertInAppPayment(paymentSourceType = PaymentSourceType.Stripe.IDEAL)
    val job = InAppPaymentRecurringContextJob.create(iap)
    job.onAdded()

    val result = job.run()
    assertThat(result.isRetry).isTrue()
    verify(atLeast = 0, atMost = 0) { AppDependencies.clientZkReceiptOperations.receiveReceiptCredential(any(), any()) }

    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)
    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.PENDING)
    assertThat(updatedIap?.data?.error?.type).isNull()
  }

  @Test
  fun `Given 400 application error, when I run, then I expect a terminal iap state`() {
    val activeSubscription = inAppPaymentsTestRule.createActiveSubscription()
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = activeSubscription)
    inAppPaymentsTestRule.initializeSubmitReceiptCredentialRequestSync(status = 400)

    val iap = insertInAppPayment(paymentSourceType = PaymentSourceType.Stripe.IDEAL)
    val job = InAppPaymentRecurringContextJob.create(iap)
    job.onAdded()

    val result = job.run()
    assertThat(result.isFailure).isTrue()
    verify(atLeast = 0, atMost = 0) { AppDependencies.clientZkReceiptOperations.receiveReceiptCredential(any(), any()) }

    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)
    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(updatedIap?.data?.error?.type).isEqualTo(InAppPaymentData.Error.Type.REDEMPTION)
  }

  @Test
  fun `Given 402 application error, when I run, then I expect a retry`() {
    val activeSubscription = inAppPaymentsTestRule.createActiveSubscription()
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = activeSubscription)
    inAppPaymentsTestRule.initializeSubmitReceiptCredentialRequestSync(status = 402)

    val iap = insertInAppPayment(paymentSourceType = PaymentSourceType.Stripe.IDEAL)
    val job = InAppPaymentRecurringContextJob.create(iap)
    job.onAdded()

    val result = job.run()
    assertThat(result.isRetry).isTrue()
    verify(atLeast = 0, atMost = 0) { AppDependencies.clientZkReceiptOperations.receiveReceiptCredential(any(), any()) }

    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)
    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.PENDING)
    assertThat(updatedIap?.data?.error?.type).isNull()
  }

  @Test
  fun `Given 403 application error, when I run, then I expect a terminal iap state`() {
    val activeSubscription = inAppPaymentsTestRule.createActiveSubscription()
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = activeSubscription)
    inAppPaymentsTestRule.initializeSubmitReceiptCredentialRequestSync(status = 403)

    val iap = insertInAppPayment(paymentSourceType = PaymentSourceType.Stripe.IDEAL)
    val job = InAppPaymentRecurringContextJob.create(iap)
    job.onAdded()

    val result = job.run()
    assertThat(result.isFailure).isTrue()
    verify(atLeast = 0, atMost = 0) { AppDependencies.clientZkReceiptOperations.receiveReceiptCredential(any(), any()) }

    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)
    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(updatedIap?.data?.error?.type).isEqualTo(InAppPaymentData.Error.Type.REDEMPTION)
  }

  @Test
  fun `Given 404 application error, when I run, then I expect a terminal iap state`() {
    val activeSubscription = inAppPaymentsTestRule.createActiveSubscription()
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = activeSubscription)
    inAppPaymentsTestRule.initializeSubmitReceiptCredentialRequestSync(status = 404)

    val iap = insertInAppPayment(paymentSourceType = PaymentSourceType.Stripe.IDEAL)
    val job = InAppPaymentRecurringContextJob.create(iap)
    job.onAdded()

    val result = job.run()
    assertThat(result.isFailure).isTrue()
    verify(atLeast = 0, atMost = 0) { AppDependencies.clientZkReceiptOperations.receiveReceiptCredential(any(), any()) }

    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)
    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(updatedIap?.data?.error?.type).isEqualTo(InAppPaymentData.Error.Type.REDEMPTION)
  }

  @Test
  fun `Given 409 application error, when I run, then I expect a terminal iap state`() {
    val activeSubscription = inAppPaymentsTestRule.createActiveSubscription()
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = activeSubscription)
    inAppPaymentsTestRule.initializeSubmitReceiptCredentialRequestSync(status = 409)

    val iap = insertInAppPayment(paymentSourceType = PaymentSourceType.Stripe.IDEAL, type = InAppPaymentType.RECURRING_BACKUP)
    val job = InAppPaymentRecurringContextJob.create(iap)
    job.onAdded()

    val result = job.run()
    assertThat(result.isFailure).isTrue()
    verify(atLeast = 0, atMost = 0) { AppDependencies.clientZkReceiptOperations.receiveReceiptCredential(any(), any()) }

    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)
    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(updatedIap?.data?.error?.type).isEqualTo(InAppPaymentData.Error.Type.REDEMPTION)
    assertThat(updatedIap?.data?.error?.data_).isEqualTo("409")
  }

  private fun insertInAppPayment(
    type: InAppPaymentType = InAppPaymentType.RECURRING_DONATION,
    state: InAppPaymentTable.State = InAppPaymentTable.State.TRANSACTING,
    subscriberId: SubscriberId? = SubscriberId.generate(),
    paymentSourceType: PaymentSourceType = PaymentSourceType.Stripe.CreditCard,
    badge: BadgeList.Badge? = null,
    redemptionState: InAppPaymentData.RedemptionState? = InAppPaymentData.RedemptionState(
      stage = InAppPaymentData.RedemptionState.Stage.INIT,
      keepAlive = false
    )
  ): InAppPaymentTable.InAppPayment {
    val iap = inAppPaymentsTestRule.createInAppPayment(type, paymentSourceType)
    SignalDatabase.inAppPayments.insert(
      type = iap.type,
      state = state,
      subscriberId = subscriberId,
      endOfPeriod = null,
      inAppPaymentData = iap.data.newBuilder()
        .badge(badge)
        .redemption(redemptionState)
        .build()
    )

    return iap
  }
}
