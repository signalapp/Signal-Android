/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.verify
import io.reactivex.rxjava3.observers.TestObserver
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.billing.BillingPurchaseState
import org.signal.core.util.deleteAll
import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatValue
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.DonationRedemptionJobStatus
import org.thoughtcrime.securesms.database.DatabaseObserver.InAppPaymentObserver
import org.thoughtcrime.securesms.database.InAppPaymentSubscriberTable
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.BadgeList
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule
import org.whispersystems.signalservice.api.storage.IAPSubscriptionId
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.math.BigDecimal
import java.util.Currency
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class InAppPaymentsRepositoryTest {

  companion object {
    private const val IAP_TOKEN = "test_token"
  }

  @get:Rule
  val signalStore = MockSignalStoreRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

  private val testAmount = FiatMoney(BigDecimal.valueOf(5), Currency.getInstance("USD")).toFiatValue()
  private val euroAmount = FiatMoney(BigDecimal.valueOf(5), Currency.getInstance("EUR")).toFiatValue()
  private val testBadge = BadgeList.Badge(id = "test-badge")

  @Before
  fun setUp() {
    SignalDatabase.inAppPayments.writableDatabase.deleteAll(InAppPaymentTable.TABLE_NAME)
    SignalDatabase.inAppPaymentSubscribers.writableDatabase.deleteAll(InAppPaymentSubscriberTable.TABLE_NAME)

    every { signalStore.inAppPayments.getRecurringDonationCurrency() } returns Currency.getInstance("USD")
  }

  /**
   * Regression test for a crash loop (issue #14872). A PayPal/Braintree charge failure carries none of Stripe's
   * outcome fields, so [ActiveSubscription.ChargeFailure.outcomeType] (and its siblings) deserialize to null.
   * Copying those straight into the non-null proto ChargeFailure previously threw an NPE on every keep-alive run,
   * leaving the app unable to start once a donation was canceled server-side.
   */
  @Test
  fun `updateInAppPaymentWithCancelation records cancelation when charge failure has null Stripe outcome fields`() {
    val subscriberId = SubscriberId.generate()
    SignalDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        subscriberId = subscriberId,
        currency = Currency.getInstance("USD"),
        type = InAppPaymentSubscriberRecord.Type.DONATION,
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.PAYPAL,
        iapSubscriptionId = null
      )
    )

    val paymentId = SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_DONATION,
      state = InAppPaymentTable.State.END,
      subscriberId = subscriberId,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData()
    )

    // Braintree/PayPal charge failure: code + message present, all Stripe outcome fields null.
    val chargeFailure = ActiveSubscription.ChargeFailure("2003", "Do Not Honor", null, null, null)
    val activeSubscription = canceledSubscription(chargeFailure)

    InAppPaymentsRepository.updateInAppPaymentWithCancelation(activeSubscription, InAppPaymentSubscriberRecord.Type.DONATION)

    val cancellation = SignalDatabase.inAppPayments.getById(paymentId)!!.data.cancellation
    assertThat(cancellation).isNotNull()
    assertThat(cancellation!!.reason).isEqualTo(InAppPaymentData.Cancellation.Reason.PAST_DUE)
    assertThat(cancellation.chargeFailure).isNotNull()
    assertThat(cancellation.chargeFailure!!.code).isEqualTo("2003")
    assertThat(cancellation.chargeFailure.outcomeType).isEqualTo("")
  }

  @Test
  fun `isPurchaseValidatedByService is false for a purchase that is not in the purchased state`() {
    insertRedeemedPayment(insertBackupSubscriber(IAP_TOKEN))

    assertThat(InAppPaymentsRepository.isPurchaseValidatedByService(purchase(state = BillingPurchaseState.PENDING))).isFalse()
  }

  @Test
  fun `isPurchaseValidatedByService is true for an acknowledged purchase with no local subscriber`() {
    assertThat(InAppPaymentsRepository.isPurchaseValidatedByService(purchase(isAcknowledged = true))).isTrue()
  }

  @Test
  fun `isPurchaseValidatedByService is true for an unacknowledged purchase whose token was redeemed`() {
    insertRedeemedPayment(insertBackupSubscriber(IAP_TOKEN))

    assertThat(InAppPaymentsRepository.isPurchaseValidatedByService(purchase())).isTrue()
  }

  /**
   * The token on the subscriber record is written as soon as the subscriber id is created, well before the service has
   * seen it, so a redemption must only vouch for the token it was actually redeemed against.
   */
  @Test
  fun `isPurchaseValidatedByService is false for an unacknowledged purchase whose token differs from the redeemed one`() {
    insertRedeemedPayment(insertBackupSubscriber("some_other_token"))

    assertThat(InAppPaymentsRepository.isPurchaseValidatedByService(purchase())).isFalse()
  }

  @Test
  fun `isPurchaseValidatedByService is false for an unacknowledged purchase whose token was never redeemed`() {
    val subscriberId = insertBackupSubscriber(IAP_TOKEN)
    SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_BACKUP,
      state = InAppPaymentTable.State.END,
      subscriberId = subscriberId,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData()
    )

    assertThat(InAppPaymentsRepository.isPurchaseValidatedByService(purchase())).isFalse()
  }

  /**
   * A recurring iDEAL donation settles as a SEPA direct debit, so it sits in [InAppPaymentTable.State.PENDING] for
   * days. The status must carry the payment method so consumers can tell a multi-day bank settlement apart from a
   * redemption that is about to complete.
   */
  @Test
  fun `observeInAppPaymentRedemption carries iDEAL through a pending receipt request`() {
    insertPendingRecurringDonation(InAppPaymentData.PaymentMethodType.IDEAL)

    val status = observeRedemption()

    assertThat(status).isEqualTo(DonationRedemptionJobStatus.PendingReceiptRequest(PaymentSourceType.Stripe.IDEAL))
    assertThat((status as DonationRedemptionJobStatus.Pending).paymentSourceType.isBankTransfer).isTrue()
  }

  @Test
  fun `observeInAppPaymentRedemption carries card through a pending receipt request`() {
    insertPendingRecurringDonation(InAppPaymentData.PaymentMethodType.CARD)

    val status = observeRedemption()

    assertThat(status).isEqualTo(DonationRedemptionJobStatus.PendingReceiptRequest(PaymentSourceType.Stripe.CreditCard))
    assertThat((status as DonationRedemptionJobStatus.Pending).paymentSourceType.isBankTransfer).isFalse()
  }

  @Test
  fun `observeInAppPaymentRedemption emits FailedSubscription for an ended recurring donation carrying an error`() {
    insertRecurringDonation(
      state = InAppPaymentTable.State.END,
      data = InAppPaymentData(
        level = 500L,
        amount = testAmount,
        error = InAppPaymentData.Error(type = InAppPaymentData.Error.Type.PAYMENT_PROCESSING)
      )
    )

    assertThat(observeRedemption()).isEqualTo(DonationRedemptionJobStatus.FailedSubscription)
  }

  @Test
  fun `observeInAppPaymentRedemption emits None for an ended recurring donation carrying a cancelation`() {
    insertRecurringDonation(
      state = InAppPaymentTable.State.END,
      data = InAppPaymentData(
        level = 500L,
        amount = testAmount,
        cancellation = InAppPaymentData.Cancellation(reason = InAppPaymentData.Cancellation.Reason.CANCELED)
      )
    )

    assertThat(observeRedemption()).isEqualTo(DonationRedemptionJobStatus.None)
  }

  @Test
  fun `observeInAppPaymentRedemption emits PendingExternalVerification while a SEPA debit awaits authorization`() {
    insertRecurringDonation(
      state = InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION,
      endOfPeriod = null,
      data = InAppPaymentData(
        level = 500L,
        amount = euroAmount,
        paymentMethodType = InAppPaymentData.PaymentMethodType.SEPA_DEBIT,
        waitForAuth = InAppPaymentData.WaitingForAuthorizationState()
      )
    )

    val status = observeRedemption()

    assertThat(status).isInstanceOf(DonationRedemptionJobStatus.PendingExternalVerification::class)
    assertThat((status as DonationRedemptionJobStatus.Pending).paymentSourceType).isEqualTo(PaymentSourceType.Stripe.SEPADebit)
  }

  @Test
  fun `observeInAppPaymentRedemption surfaces the non-verified monthly donation for an iDEAL authorization`() {
    insertRecurringDonation(
      state = InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION,
      endOfPeriod = null,
      data = InAppPaymentData(
        level = 500L,
        amount = euroAmount,
        paymentMethodType = InAppPaymentData.PaymentMethodType.IDEAL,
        waitForAuth = InAppPaymentData.WaitingForAuthorizationState(checkedVerification = true)
      )
    )

    val status = observeRedemption()

    assertThat(status).isInstanceOf(DonationRedemptionJobStatus.PendingExternalVerification::class)
    val verification = status as DonationRedemptionJobStatus.PendingExternalVerification
    assertThat(verification.nonVerifiedMonthlyDonation).isNotNull()
    assertThat(verification.nonVerifiedMonthlyDonation!!.checkedVerification).isTrue()
  }

  @Test
  fun `observeInAppPaymentRedemption emits PendingKeepAlive for a keep-alive redemption`() {
    insertRecurringDonation(
      state = InAppPaymentTable.State.PENDING,
      data = InAppPaymentData(
        level = 500L,
        amount = testAmount,
        redemption = InAppPaymentData.RedemptionState(
          stage = InAppPaymentData.RedemptionState.Stage.INIT,
          keepAlive = true
        )
      )
    )

    assertThat(observeRedemption()).isEqualTo(DonationRedemptionJobStatus.PendingKeepAlive(PaymentSourceType.Unknown))
  }

  @Test
  fun `observeInAppPaymentRedemption emits PendingReceiptRequest for a pending one-time donation`() {
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

    val status = observeRedemption(InAppPaymentType.ONE_TIME_DONATION)

    assertThat(status).isEqualTo(DonationRedemptionJobStatus.PendingReceiptRequest(PaymentSourceType.Unknown))
  }

  @Test
  fun `observeInAppPaymentRedemption re-emits a new status when the payment row changes`() {
    val id = insertRecurringDonation(
      state = InAppPaymentTable.State.PENDING,
      data = InAppPaymentData(
        level = 500L,
        amount = testAmount,
        redemption = InAppPaymentData.RedemptionState(
          stage = InAppPaymentData.RedemptionState.Stage.INIT,
          keepAlive = true
        )
      )
    )

    val testObserver = TestObserver<DonationRedemptionJobStatus>()
    InAppPaymentsRepository.observeInAppPaymentRedemption(InAppPaymentType.RECURRING_DONATION)
      .take(2)
      .subscribe(testObserver)

    testObserver.awaitCount(1)
    assertThat(testObserver.values().first()).isEqualTo(DonationRedemptionJobStatus.PendingKeepAlive(PaymentSourceType.Unknown))

    // databaseObserver is a relaxed mock that is never cleared, so its call history spans the suite. Capture into a
    // list and take the most recent registration, which is the subscription made above.
    val registered = mutableListOf<InAppPaymentObserver>()
    verify { AppDependencies.databaseObserver.registerInAppPaymentObserver(capture(registered)) }

    val payment = SignalDatabase.inAppPayments.getById(id)!!
    val updated = payment.copy(
      state = InAppPaymentTable.State.END,
      data = payment.data.copy(
        error = InAppPaymentData.Error(type = InAppPaymentData.Error.Type.PAYMENT_PROCESSING)
      )
    )
    SignalDatabase.inAppPayments.update(updated)
    registered.last().onInAppPaymentChanged(updated)

    testObserver.awaitCount(2)
    assertThat(testObserver.values().last()).isEqualTo(DonationRedemptionJobStatus.FailedSubscription)
  }

  private fun insertPendingRecurringDonation(paymentMethodType: InAppPaymentData.PaymentMethodType) {
    insertRecurringDonation(
      state = InAppPaymentTable.State.PENDING,
      data = InAppPaymentData(
        level = 500L,
        paymentMethodType = paymentMethodType,
        redemption = InAppPaymentData.RedemptionState(
          stage = InAppPaymentData.RedemptionState.Stage.CONVERSION_STARTED
        )
      )
    )
  }

  private fun insertRecurringDonation(
    state: InAppPaymentTable.State,
    data: InAppPaymentData,
    endOfPeriod: Duration? = 1000.seconds
  ): InAppPaymentTable.InAppPaymentId {
    return SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_DONATION,
      state = state,
      subscriberId = null,
      endOfPeriod = endOfPeriod,
      inAppPaymentData = data
    )
  }

  private fun observeRedemption(type: InAppPaymentType = InAppPaymentType.RECURRING_DONATION): DonationRedemptionJobStatus {
    return InAppPaymentsRepository.observeInAppPaymentRedemption(type).blockingFirst()
  }

  private fun purchase(
    state: BillingPurchaseState = BillingPurchaseState.PURCHASED,
    isAcknowledged: Boolean = false,
    purchaseToken: String = IAP_TOKEN
  ): BillingPurchaseResult {
    return BillingPurchaseResult.Success(
      purchaseState = state,
      purchaseToken = purchaseToken,
      isAcknowledged = isAcknowledged,
      purchaseTime = System.currentTimeMillis(),
      isAutoRenewing = true
    )
  }

  private fun insertBackupSubscriber(token: String): SubscriberId {
    val subscriberId = SubscriberId.generate()

    SignalDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        subscriberId = subscriberId,
        currency = null,
        type = InAppPaymentSubscriberRecord.Type.BACKUP,
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
        iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken(token)
      )
    )

    return subscriberId
  }

  private fun insertRedeemedPayment(subscriberId: SubscriberId) {
    SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_BACKUP,
      state = InAppPaymentTable.State.END,
      subscriberId = subscriberId,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData(
        redemption = InAppPaymentData.RedemptionState(stage = InAppPaymentData.RedemptionState.Stage.REDEEMED)
      )
    )
  }

  private fun canceledSubscription(chargeFailure: ActiveSubscription.ChargeFailure?): ActiveSubscription {
    val periodEnd = System.currentTimeMillis().milliseconds.inWholeSeconds + 45.days.inWholeSeconds
    return ActiveSubscription(
      ActiveSubscription.Subscription(
        2000,
        "USD",
        BigDecimal.ONE,
        periodEnd,
        false,
        periodEnd,
        false,
        "canceled",
        "BRAINTREE",
        "PAYPAL",
        false
      ),
      chargeFailure
    )
  }
}
