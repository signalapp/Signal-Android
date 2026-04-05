/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.manage

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.reactivex.rxjava3.observers.TestObserver
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.deleteAll
import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatValue
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.BadgeList
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.testing.SignalActivityRule
import java.math.BigDecimal
import java.util.Currency
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class ManageDonationsViewModelTest {

  @get:Rule
  val harness = SignalActivityRule()

  private val testAmount = FiatMoney(BigDecimal.valueOf(5), Currency.getInstance("USD")).toFiatValue()
  private val testBadge = BadgeList.Badge(id = "test-badge")

  @Before
  fun setUp() {
    SignalDatabase.inAppPayments.writableDatabase.deleteAll(InAppPaymentTable.TABLE_NAME)
  }

  @Test
  fun givenEndRecordWithNoError_whenIQueryLatest_thenIGetActiveSubscription() {
    SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_DONATION,
      state = InAppPaymentTable.State.END,
      subscriberId = null,
      endOfPeriod = 1000.seconds,
      inAppPaymentData = InAppPaymentData(
        level = 500L,
        amount = testAmount,
        badge = testBadge
      )
    )

    val latest = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_DONATION)
    assertThat(latest).isNotNull()
    assertThat(latest!!.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(latest.data.cancellation).isNull()
  }

  @Test
  fun givenEmptyDatabase_whenIQueryLatest_thenIGetNull() {
    val latest = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_DONATION)
    assertThat(latest).isNull()
  }

  @Test
  fun givenTransactingRecord_whenIQueryLatest_thenItIsReturned() {
    val id = SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_DONATION,
      state = InAppPaymentTable.State.CREATED,
      subscriberId = null,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData(
        level = 500L,
        amount = testAmount
      )
    )

    SignalDatabase.inAppPayments.moveToTransacting(id)

    val latest = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_DONATION)
    assertThat(latest).isNotNull()
    assertThat(latest!!.state).isEqualTo(InAppPaymentTable.State.TRANSACTING)
  }

  @Test
  fun givenCreatedRecord_whenIQueryLatest_thenItIsFiltered() {
    SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_DONATION,
      state = InAppPaymentTable.State.CREATED,
      subscriberId = null,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData()
    )

    val latest = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_DONATION)
    assertThat(latest).isNull()
  }

  @Test
  fun givenEndRecordWithError_whenIObserveRedemption_thenIGetFailedSubscription() {
    SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_DONATION,
      state = InAppPaymentTable.State.END,
      subscriberId = null,
      endOfPeriod = 1000.seconds,
      inAppPaymentData = InAppPaymentData(
        level = 500L,
        amount = testAmount,
        error = InAppPaymentData.Error(type = InAppPaymentData.Error.Type.PAYMENT_PROCESSING)
      )
    )

    val observer = TestObserver<DonationRedemptionJobStatus>()
    InAppPaymentsRepository.observeInAppPaymentRedemption(InAppPaymentType.RECURRING_DONATION)
      .take(1)
      .subscribe(observer)
    observer.awaitCount(1)
    observer.assertValue(DonationRedemptionJobStatus.FailedSubscription)
  }

  @Test
  fun givenEndRecordWithCancellation_whenIObserveRedemption_thenIGetNone() {
    SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_DONATION,
      state = InAppPaymentTable.State.END,
      subscriberId = null,
      endOfPeriod = 1000.seconds,
      inAppPaymentData = InAppPaymentData(
        level = 500L,
        amount = testAmount,
        cancellation = InAppPaymentData.Cancellation(reason = InAppPaymentData.Cancellation.Reason.CANCELED)
      )
    )

    val observer = TestObserver<DonationRedemptionJobStatus>()
    InAppPaymentsRepository.observeInAppPaymentRedemption(InAppPaymentType.RECURRING_DONATION)
      .take(1)
      .subscribe(observer)
    observer.awaitCount(1)
    observer.assertValue(DonationRedemptionJobStatus.None)
  }

  @Test
  fun givenPendingBankTransferRecord_whenIObserveRedemption_thenIGetPendingExternalVerification() {
    SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_DONATION,
      state = InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION,
      subscriberId = null,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData(
        level = 500L,
        amount = FiatMoney(BigDecimal.valueOf(5), Currency.getInstance("EUR")).toFiatValue(),
        paymentMethodType = InAppPaymentData.PaymentMethodType.SEPA_DEBIT,
        waitForAuth = InAppPaymentData.WaitingForAuthorizationState()
      )
    )

    val observer = TestObserver<DonationRedemptionJobStatus>()
    InAppPaymentsRepository.observeInAppPaymentRedemption(InAppPaymentType.RECURRING_DONATION)
      .take(1)
      .subscribe(observer)
    observer.awaitCount(1)

    val status = observer.values().first()
    assertThat(status is DonationRedemptionJobStatus.PendingExternalVerification).isTrue()
  }

  @Test
  fun givenKeepAlivePendingRecord_whenIObserveRedemption_thenIGetPendingKeepAlive() {
    SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_DONATION,
      state = InAppPaymentTable.State.PENDING,
      subscriberId = null,
      endOfPeriod = 1000.seconds,
      inAppPaymentData = InAppPaymentData(
        level = 500L,
        amount = testAmount,
        redemption = InAppPaymentData.RedemptionState(
          stage = InAppPaymentData.RedemptionState.Stage.INIT,
          keepAlive = true
        )
      )
    )

    val observer = TestObserver<DonationRedemptionJobStatus>()
    InAppPaymentsRepository.observeInAppPaymentRedemption(InAppPaymentType.RECURRING_DONATION)
      .take(1)
      .subscribe(observer)
    observer.awaitCount(1)
    observer.assertValue(DonationRedemptionJobStatus.PendingKeepAlive)
  }

  @Test
  fun givenPendingOneTimeDonation_whenIObserveRedemption_thenIGetPendingStatus() {
    SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.ONE_TIME_DONATION,
      state = InAppPaymentTable.State.PENDING,
      subscriberId = null,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData(
        level = 1L,
        amount = testAmount,
        badge = testBadge,
        redemption = InAppPaymentData.RedemptionState(
          stage = InAppPaymentData.RedemptionState.Stage.INIT
        )
      )
    )

    val observer = TestObserver<DonationRedemptionJobStatus>()
    InAppPaymentsRepository.observeInAppPaymentRedemption(InAppPaymentType.ONE_TIME_DONATION)
      .take(1)
      .subscribe(observer)
    observer.awaitCount(1)
    observer.assertValue(DonationRedemptionJobStatus.PendingReceiptRequest)
  }

  @Test
  fun givenEndRecordWithNonRedemptionError_whenICheckPaymentFailure_thenItIsTrue() {
    val id = SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_DONATION,
      state = InAppPaymentTable.State.END,
      subscriberId = null,
      endOfPeriod = 1000.seconds,
      inAppPaymentData = InAppPaymentData(
        level = 500L,
        amount = testAmount,
        error = InAppPaymentData.Error(type = InAppPaymentData.Error.Type.PAYMENT_PROCESSING)
      )
    )

    val payment = SignalDatabase.inAppPayments.getById(id)!!
    val isPaymentFailure = payment.data.error?.let {
      it.type != InAppPaymentData.Error.Type.REDEMPTION
    } ?: false

    assertThat(isPaymentFailure).isTrue()
  }

  @Test
  fun givenEndRecordWithRedemptionError_whenICheckPaymentFailure_thenItIsFalse() {
    val id = SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_DONATION,
      state = InAppPaymentTable.State.END,
      subscriberId = null,
      endOfPeriod = 1000.seconds,
      inAppPaymentData = InAppPaymentData(
        level = 500L,
        amount = testAmount,
        error = InAppPaymentData.Error(type = InAppPaymentData.Error.Type.REDEMPTION)
      )
    )

    val payment = SignalDatabase.inAppPayments.getById(id)!!
    val isPaymentFailure = payment.data.error?.let {
      it.type != InAppPaymentData.Error.Type.REDEMPTION
    } ?: false

    assertThat(isPaymentFailure).isEqualTo(false)
  }

  @Test
  fun givenStateTransition_whenIUpdateRecord_thenObserverSeesNewState() {
    val id = SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_DONATION,
      state = InAppPaymentTable.State.PENDING,
      subscriberId = null,
      endOfPeriod = 1000.seconds,
      inAppPaymentData = InAppPaymentData(
        level = 500L,
        amount = testAmount,
        redemption = InAppPaymentData.RedemptionState(
          stage = InAppPaymentData.RedemptionState.Stage.INIT,
          keepAlive = true
        )
      )
    )

    val observer = TestObserver<DonationRedemptionJobStatus>()
    InAppPaymentsRepository.observeInAppPaymentRedemption(InAppPaymentType.RECURRING_DONATION)
      .take(2)
      .subscribe(observer)

    observer.awaitCount(1)
    assertThat(observer.values().first()).isEqualTo(DonationRedemptionJobStatus.PendingKeepAlive)

    val payment = SignalDatabase.inAppPayments.getById(id)!!
    SignalDatabase.inAppPayments.update(
      payment.copy(
        state = InAppPaymentTable.State.END,
        data = payment.data.copy(
          error = InAppPaymentData.Error(type = InAppPaymentData.Error.Type.PAYMENT_PROCESSING)
        )
      )
    )

    observer.awaitCount(2)
    assertThat(observer.values().last()).isEqualTo(DonationRedemptionJobStatus.FailedSubscription)
  }

  @Test
  fun givenNonVerifiedIdealRecurring_whenIObserveRedemption_thenIGetNonVerifiedMonthlyDonation() {
    SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_DONATION,
      state = InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION,
      subscriberId = null,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData(
        level = 500L,
        amount = FiatMoney(BigDecimal.valueOf(5), Currency.getInstance("EUR")).toFiatValue(),
        paymentMethodType = InAppPaymentData.PaymentMethodType.IDEAL,
        waitForAuth = InAppPaymentData.WaitingForAuthorizationState(checkedVerification = true)
      )
    )

    val observer = TestObserver<DonationRedemptionJobStatus>()
    InAppPaymentsRepository.observeInAppPaymentRedemption(InAppPaymentType.RECURRING_DONATION)
      .take(1)
      .subscribe(observer)
    observer.awaitCount(1)

    val status = observer.values().first()
    assertThat(status is DonationRedemptionJobStatus.PendingExternalVerification).isTrue()
    val verification = status as DonationRedemptionJobStatus.PendingExternalVerification
    assertThat(verification.nonVerifiedMonthlyDonation).isNotNull()
    assertThat(verification.nonVerifiedMonthlyDonation!!.checkedVerification).isTrue()
  }
}
