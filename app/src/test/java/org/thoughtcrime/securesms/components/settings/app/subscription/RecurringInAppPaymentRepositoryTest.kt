package org.thoughtcrime.securesms.components.settings.app.subscription

import android.app.Application
import androidx.lifecycle.AtomicReference
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import io.reactivex.rxjava3.core.Flowable
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.assertIsNot
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentMethodType
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.subscription.LevelUpdate
import org.thoughtcrime.securesms.subscription.LevelUpdateOperation
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.RxPluginsRule
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.subscriptions.IdempotencyKey
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.internal.EmptyResponse
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import org.whispersystems.signalservice.internal.util.JsonUtil
import java.util.Currency
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecurringInAppPaymentRepositoryTest {

  private val testConfigData: SubscriptionsConfiguration by lazy {
    val testConfigJsonData = javaClass.classLoader!!.getResourceAsStream("donations_configuration_test_data.json").bufferedReader().readText()

    JsonUtil.fromJson(testConfigJsonData, SubscriptionsConfiguration::class.java)
  }

  @get:Rule
  val rxRule = RxPluginsRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @Before
  fun setUp() {
    mockkStatic(RemoteConfig::class)
    every { RemoteConfig.init() } just runs

    mockkObject(InAppDonations)
    every { InAppDonations.isPayPalAvailable() } returns true
    every { InAppDonations.isGooglePayAvailable() } returns true
    every { InAppDonations.isSEPADebitAvailable() } returns true
    every { InAppDonations.isCreditCardAvailable() } returns true
    every { InAppDonations.isIDEALAvailable() } returns true

    mockkStatic(InAppPaymentsRepository::class)
    mockkObject(InAppPaymentsRepository)
    every { InAppPaymentsRepository.scheduleSyncForAccountRecordChange() } returns Unit

    mockkObject(SignalStore.Companion)
    every { SignalStore.Companion.inAppPayments } returns mockk {
      every { SignalStore.Companion.inAppPayments.getSubscriptionCurrency(any()) } returns Currency.getInstance("USD")
      every { SignalStore.Companion.inAppPayments.updateLocalStateForManualCancellation(any()) } returns Unit
      every { SignalStore.Companion.inAppPayments.updateLocalStateForLocalSubscribe(any()) } returns Unit
    }

    mockkObject(SignalDatabase.Companion)
    every { SignalDatabase.Companion.recipients } returns mockk {
      every { SignalDatabase.Companion.recipients.markNeedsSync(any<RecipientId>()) } returns Unit
    }

    every { SignalDatabase.Companion.inAppPayments } returns mockk {
      every { SignalDatabase.Companion.inAppPayments.update(any()) } returns Unit
    }

    mockkStatic(StorageSyncHelper::class)
    every { StorageSyncHelper.scheduleSyncForDataChange() } returns Unit

    every { AppDependencies.donationsService.putSubscription(any()) } returns ServiceResponse.forResult(EmptyResponse.INSTANCE, 200, "")
    every { AppDependencies.donationsService.updateSubscriptionLevel(any(), any(), any(), any(), any()) } returns ServiceResponse.forResult(EmptyResponse.INSTANCE, 200, "")
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `when I getDonationsConfiguration then I expect a set of three Subscription objects`() {
    every { AppDependencies.donationsService.getDonationsConfiguration(any()) } returns ServiceResponse(200, "", testConfigData, null, null)

    val testObserver = RecurringInAppPaymentRepository.getSubscriptions().test()
    rxRule.defaultScheduler.triggerActions()

    testObserver
      .assertComplete()
      .assertValueCount(1)
      .assertValue { it.size == 3 }
  }

  @Test
  fun `Given I do not need to rotate my subscriber id, when I ensureSubscriberId, then I use the same subscriber id`() {
    val initialSubscriber = createSubscriber()
    val ref = mockLocalSubscriberAccess(initialSubscriber)

    val testObserver = RecurringInAppPaymentRepository.ensureSubscriberId(
      subscriberType = InAppPaymentSubscriberRecord.Type.DONATION,
      isRotation = false
    ).test()

    rxRule.defaultScheduler.triggerActions()
    testObserver.assertComplete()

    val newSubscriber = ref.get()

    newSubscriber assertIsNot initialSubscriber
  }

  @Test
  fun `Given I need to rotate my subscriber id, when I ensureSubscriberId, then I generate and set a new subscriber id`() {
    val initialSubscriber = createSubscriber()
    val ref = mockLocalSubscriberAccess(initialSubscriber)

    val testObserver = RecurringInAppPaymentRepository.ensureSubscriberId(
      subscriberType = InAppPaymentSubscriberRecord.Type.DONATION,
      isRotation = true
    ).test()

    rxRule.defaultScheduler.triggerActions()
    testObserver.assertComplete()

    val newSubscriber = ref.get()

    newSubscriber assertIsNot initialSubscriber
  }

  @Test
  fun `Given no current subscriber, when I rotateSubscriberId, then I do not try to cancel subscription`() {
    val ref = mockLocalSubscriberAccess()

    val testObserver = RecurringInAppPaymentRepository.rotateSubscriberId(InAppPaymentSubscriberRecord.Type.DONATION).test()

    rxRule.defaultScheduler.triggerActions()
    testObserver.assertComplete()

    ref.get() assertIsNot null
    verify(inverse = true) {
      AppDependencies.donationsService.cancelSubscription(any())
    }
  }

  @Test
  fun `Given current subscriber, when I rotateSubscriberId, then I do not try to cancel subscription`() {
    val initialSubscriber = createSubscriber()
    val ref = mockLocalSubscriberAccess(initialSubscriber)

    val testObserver = RecurringInAppPaymentRepository.rotateSubscriberId(InAppPaymentSubscriberRecord.Type.DONATION).test()

    rxRule.defaultScheduler.triggerActions()
    testObserver.assertComplete()

    ref.get() assertIsNot null
    verify {
      AppDependencies.donationsService.cancelSubscription(any())
    }
  }

  @Test
  fun `given no delays, when I setSubscriptionLevel, then I expect happy path`() {
    val paymentSourceType = PaymentSourceType.Stripe.CreditCard
    val inAppPayment = createInAppPayment(paymentSourceType)
    mockLocalSubscriberAccess(createSubscriber())

    every { SignalStore.inAppPayments.getLevelOperation("500") } returns LevelUpdateOperation(IdempotencyKey.generate(), "500")
    every { SignalDatabase.inAppPayments.getById(any()) } returns inAppPayment
    every { InAppPaymentsRepository.observeUpdates(inAppPayment.id) } returns Flowable.just(inAppPayment.copy(state = InAppPaymentTable.State.END))

    val testObserver = RecurringInAppPaymentRepository.setSubscriptionLevel(inAppPayment, paymentSourceType).test()
    val processingUpdate = LevelUpdate.isProcessing.test()

    rxRule.defaultScheduler.triggerActions()

    processingUpdate.assertValues(false, true, false)

    testObserver.assertComplete()
  }

  @Test
  fun `given 10s delay, when I setSubscriptionLevel, then I expect timeout`() {
    val paymentSourceType = PaymentSourceType.Stripe.CreditCard
    val inAppPayment = createInAppPayment(paymentSourceType)
    mockLocalSubscriberAccess(createSubscriber())

    every { SignalStore.inAppPayments.getLevelOperation("500") } returns LevelUpdateOperation(IdempotencyKey.generate(), "500")
    every { SignalDatabase.inAppPayments.getById(any()) } returns inAppPayment

    val testObserver = RecurringInAppPaymentRepository.setSubscriptionLevel(inAppPayment, paymentSourceType).test()
    val processingUpdate = LevelUpdate.isProcessing.test()

    rxRule.defaultScheduler.triggerActions()

    processingUpdate.assertValues(false, true, false)

    rxRule.defaultScheduler.advanceTimeBy(10, TimeUnit.SECONDS)
    rxRule.defaultScheduler.triggerActions()

    testObserver.assertError {
      it is DonationError.BadgeRedemptionError.TimeoutWaitingForTokenError
    }
  }

  @Test
  fun `given long running payment type with 10s delay, when I setSubscriptionLevel, then I expect pending`() {
    val paymentSourceType = PaymentSourceType.Stripe.SEPADebit
    val inAppPayment = createInAppPayment(paymentSourceType)
    mockLocalSubscriberAccess(createSubscriber())

    every { SignalStore.inAppPayments.getLevelOperation("500") } returns LevelUpdateOperation(IdempotencyKey.generate(), "500")
    every { SignalDatabase.inAppPayments.getById(any()) } returns inAppPayment

    val testObserver = RecurringInAppPaymentRepository.setSubscriptionLevel(inAppPayment, paymentSourceType).test()
    val processingUpdate = LevelUpdate.isProcessing.test()

    rxRule.defaultScheduler.triggerActions()

    processingUpdate.assertValues(false, true, false)

    rxRule.defaultScheduler.advanceTimeBy(10, TimeUnit.SECONDS)
    rxRule.defaultScheduler.triggerActions()

    testObserver.assertError {
      it is DonationError.BadgeRedemptionError.DonationPending
    }
  }

  @Test
  fun `given an execution error, when I setSubscriptionLevel, then I expect the same error`() {
    val expected = NonSuccessfulResponseCodeException(404)
    val paymentSourceType = PaymentSourceType.Stripe.SEPADebit
    val inAppPayment = createInAppPayment(paymentSourceType)
    mockLocalSubscriberAccess(createSubscriber())

    every { SignalStore.inAppPayments.getLevelOperation("500") } returns LevelUpdateOperation(IdempotencyKey.generate(), "500")
    every { SignalDatabase.inAppPayments.getById(any()) } returns inAppPayment
    every { AppDependencies.donationsService.updateSubscriptionLevel(any(), any(), any(), any(), any()) } returns ServiceResponse.forExecutionError(expected)

    val testObserver = RecurringInAppPaymentRepository.setSubscriptionLevel(inAppPayment, paymentSourceType).test()
    val processingUpdate = LevelUpdate.isProcessing.distinctUntilChanged().test()

    rxRule.defaultScheduler.triggerActions()

    processingUpdate.assertValues(false, true, false)

    rxRule.defaultScheduler.advanceTimeBy(10, TimeUnit.SECONDS)
    rxRule.defaultScheduler.triggerActions()

    testObserver.assertError(expected)
  }

  private fun createSubscriber(): InAppPaymentSubscriberRecord {
    return InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = Currency.getInstance("USD"),
      type = InAppPaymentSubscriberRecord.Type.DONATION,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.CARD
    )
  }

  private fun createInAppPayment(
    paymentSourceType: PaymentSourceType
  ): InAppPaymentTable.InAppPayment {
    return InAppPaymentTable.InAppPayment(
      id = InAppPaymentTable.InAppPaymentId(1),
      state = InAppPaymentTable.State.CREATED,
      insertedAt = System.currentTimeMillis().milliseconds,
      updatedAt = System.currentTimeMillis().milliseconds,
      notified = true,
      subscriberId = null,
      endOfPeriod = 0.milliseconds,
      type = InAppPaymentType.RECURRING_DONATION,
      data = InAppPaymentData(
        badge = null,
        level = 500,
        paymentMethodType = paymentSourceType.toPaymentMethodType()
      )
    )
  }

  private fun mockLocalSubscriberAccess(initialSubscriber: InAppPaymentSubscriberRecord? = null): AtomicReference<InAppPaymentSubscriberRecord?> {
    val ref = AtomicReference(initialSubscriber)
    every { InAppPaymentsRepository.getSubscriber(any()) } answers { ref.get() }
    every { InAppPaymentsRepository.setSubscriber(any()) } answers { ref.set(firstArg()) }

    return ref
  }
}
