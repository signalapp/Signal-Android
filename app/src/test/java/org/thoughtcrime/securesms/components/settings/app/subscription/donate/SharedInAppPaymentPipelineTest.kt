package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import android.app.Application
import assertk.assertThat
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.verify
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.processors.PublishProcessor
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.donations.CreditCardPaymentSource
import org.signal.donations.InAppPaymentType
import org.signal.donations.PayPalPaymentSource
import org.signal.donations.SEPADebitPaymentSource
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toErrorSource
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsTestRule
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.InAppPaymentPayPalOneTimeSetupJob
import org.thoughtcrime.securesms.jobs.InAppPaymentPayPalRecurringSetupJob
import org.thoughtcrime.securesms.jobs.InAppPaymentStripeOneTimeSetupJob
import org.thoughtcrime.securesms.jobs.InAppPaymentStripeRecurringSetupJob
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.RxPluginsRule
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SharedInAppPaymentPipelineTest {

  @get:Rule
  val rxRule = RxPluginsRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val iapRule = InAppPaymentsTestRule()

  private val updateDispatcher = PublishProcessor.create<InAppPaymentTable.InAppPayment>()

  @Before
  fun setUp() {
    every { InAppPaymentsRepository.observeUpdates(any()) } returns updateDispatcher
  }

  @Test
  fun `Given a recurring PayPal donation, when I awaitTransaction, then I expect to add InAppPaymentPayPalRecurringSetupJob`() {
    val inAppPayment = createInAppPayment()
    val paymentSource = PayPalPaymentSource()
    val requiredActionHandler: RequiredActionHandler = {
      Completable.complete()
    }

    every { SignalDatabase.inAppPayments.getById(inAppPayment.id) } returns inAppPayment

    val test = SharedInAppPaymentPipeline.awaitTransaction(
      inAppPayment.id,
      paymentSource,
      requiredActionHandler,
      requiredActionHandler
    ).test()

    test.assertNoErrors()

    verify {
      AppDependencies.jobManager.add(ofType(InAppPaymentPayPalRecurringSetupJob::class))
    }

    test.dispose()
  }

  @Test
  fun `Given a recurring Stripe donation, when I awaitTransaction, then I expect to add InAppPaymentPayPalRecurringSetupJob`() {
    val inAppPayment = createInAppPayment(
      paymentMethodType = InAppPaymentData.PaymentMethodType.CARD
    )

    val paymentSource = CreditCardPaymentSource(
      payload = JSONObject().apply {
        put("id", "token-id")
      }
    )

    val requiredActionHandler: RequiredActionHandler = {
      Completable.complete()
    }

    every { SignalDatabase.inAppPayments.getById(inAppPayment.id) } returns inAppPayment

    val test = SharedInAppPaymentPipeline.awaitTransaction(
      inAppPayment.id,
      paymentSource,
      requiredActionHandler,
      requiredActionHandler
    ).test()

    test.assertNoErrors()

    verify {
      AppDependencies.jobManager.add(ofType(InAppPaymentStripeRecurringSetupJob::class))
    }

    test.dispose()
  }

  @Test
  fun `Given a one time PayPal donation, when I awaitTransaction, then I expect to add InAppPaymentPayPalOneTimeSetupJob`() {
    val inAppPayment = createInAppPayment(
      type = InAppPaymentType.ONE_TIME_DONATION
    )

    val paymentSource = PayPalPaymentSource()
    val requiredActionHandler: RequiredActionHandler = {
      Completable.complete()
    }

    every { SignalDatabase.inAppPayments.getById(inAppPayment.id) } returns inAppPayment

    val test = SharedInAppPaymentPipeline.awaitTransaction(
      inAppPayment.id,
      paymentSource,
      requiredActionHandler,
      requiredActionHandler
    ).test()

    test.assertNoErrors()

    verify {
      AppDependencies.jobManager.add(ofType(InAppPaymentPayPalOneTimeSetupJob::class))
    }

    test.dispose()
  }

  @Test
  fun `Given a one time Stripe donation, when I awaitTransaction, then I expect to add InAppPaymentPayPalOneTimeSetupJob`() {
    val inAppPayment = createInAppPayment(
      type = InAppPaymentType.ONE_TIME_DONATION,
      paymentMethodType = InAppPaymentData.PaymentMethodType.CARD
    )

    val paymentSource = CreditCardPaymentSource(
      payload = JSONObject().apply {
        put("id", "token-id")
      }
    )

    val requiredActionHandler: RequiredActionHandler = {
      Completable.complete()
    }

    every { SignalDatabase.inAppPayments.getById(inAppPayment.id) } returns inAppPayment

    val test = SharedInAppPaymentPipeline.awaitTransaction(
      inAppPayment.id,
      paymentSource,
      requiredActionHandler,
      requiredActionHandler
    ).test()

    test.assertNoErrors()

    verify {
      AppDependencies.jobManager.add(ofType(InAppPaymentStripeOneTimeSetupJob::class))
    }

    test.dispose()
  }

  @Test
  fun `Given END state with error, when I awaitTransaction, then I expect error`() {
    val inAppPayment = createInAppPayment()
    val paymentSource = PayPalPaymentSource()
    val requiredActionHandler: RequiredActionHandler = {
      Completable.complete()
    }

    every { SignalDatabase.inAppPayments.getById(inAppPayment.id) } returns inAppPayment

    val test = SharedInAppPaymentPipeline.awaitTransaction(
      inAppPayment.id,
      paymentSource,
      requiredActionHandler,
      requiredActionHandler
    ).test()

    test.assertNoErrors()

    val errorPayment = inAppPayment.copy(
      state = InAppPaymentTable.State.END,
      data = inAppPayment.data.newBuilder().error(
        error = InAppPaymentData.Error(
          type = InAppPaymentData.Error.Type.PAYMENT_PROCESSING
        )
      ).build()
    )

    updateDispatcher.onNext(errorPayment)

    test.assertError { throwable ->
      throwable is InAppPaymentError && throwable.inAppPaymentDataError == errorPayment.data.error
    }
  }

  @Test
  fun `Given END state without error, when I awaitTransaction, then I expect error`() {
    val inAppPayment = createInAppPayment()
    val paymentSource = PayPalPaymentSource()
    val requiredActionHandler: RequiredActionHandler = {
      Completable.complete()
    }

    every { SignalDatabase.inAppPayments.getById(inAppPayment.id) } returns inAppPayment

    val test = SharedInAppPaymentPipeline.awaitTransaction(
      inAppPayment.id,
      paymentSource,
      requiredActionHandler,
      requiredActionHandler
    ).test()

    test.assertNoErrors()

    val errorPayment = inAppPayment.copy(
      state = InAppPaymentTable.State.END
    )

    updateDispatcher.onNext(errorPayment)

    test.assertError { throwable ->
      throwable is DonationError && throwable.source == inAppPayment.type.toErrorSource()
    }
  }

  @Test
  fun `Given REQUIRES_ACTION state, when I awaitTransaction, then I expect re-trigger`() {
    val inAppPayment = createInAppPayment()
    val paymentSource = PayPalPaymentSource()
    var wasCalled = false
    val requiredActionHandler: RequiredActionHandler = {
      wasCalled = true
      Completable.complete()
    }

    every { SignalDatabase.inAppPayments.getById(inAppPayment.id) } returns inAppPayment

    val test = SharedInAppPaymentPipeline.awaitTransaction(
      inAppPayment.id,
      paymentSource,
      requiredActionHandler,
      requiredActionHandler
    ).test()

    test.assertNoErrors()

    val requiresAction = inAppPayment.copy(
      state = InAppPaymentTable.State.REQUIRES_ACTION
    )

    updateDispatcher.onNext(requiresAction)

    assertThat(wasCalled).isTrue()

    verify(exactly = 2) {
      AppDependencies.jobManager.add(ofType(InAppPaymentPayPalRecurringSetupJob::class))
    }

    test.dispose()
  }

  @Test
  fun `Given PENDING state transitions to END state without error, when I awaitTransaction, then I expect to complete`() {
    val inAppPayment = createInAppPayment()
    val paymentSource = PayPalPaymentSource()
    val requiredActionHandler: RequiredActionHandler = {
      Completable.complete()
    }

    every { SignalDatabase.inAppPayments.getById(inAppPayment.id) } returns inAppPayment

    val test = SharedInAppPaymentPipeline.awaitTransaction(
      inAppPayment.id,
      paymentSource,
      requiredActionHandler,
      requiredActionHandler
    ).test()

    test.assertNoErrors()

    updateDispatcher.onNext(
      inAppPayment.copy(
        state = InAppPaymentTable.State.PENDING
      )
    )

    updateDispatcher.onNext(
      inAppPayment.copy(
        state = InAppPaymentTable.State.END
      )
    )

    test.assertComplete()
  }

  @Test
  fun `Given PENDING state transitions to END state with error, when I awaitTransaction, then I expect to complete`() {
    val inAppPayment = createInAppPayment()
    val paymentSource = PayPalPaymentSource()
    val requiredActionHandler: RequiredActionHandler = {
      Completable.complete()
    }

    every { SignalDatabase.inAppPayments.getById(inAppPayment.id) } returns inAppPayment

    val test = SharedInAppPaymentPipeline.awaitTransaction(
      inAppPayment.id,
      paymentSource,
      requiredActionHandler,
      requiredActionHandler
    ).test()

    test.assertNoErrors()

    updateDispatcher.onNext(
      inAppPayment.copy(
        state = InAppPaymentTable.State.PENDING
      )
    )

    val errorData = inAppPayment.data.newBuilder().error(
      error = InAppPaymentData.Error(
        type = InAppPaymentData.Error.Type.REDEMPTION
      )
    ).build()

    updateDispatcher.onNext(
      inAppPayment.copy(
        state = InAppPaymentTable.State.END,
        data = errorData
      )
    )

    test.assertError {
      it is InAppPaymentError && it.inAppPaymentDataError == errorData.error
    }
  }

  @Test
  fun `Given PENDING state that times out, when I awaitTransaction, then I expect TimeoutWaitingForTokenError`() {
    val inAppPayment = createInAppPayment()
    val paymentSource = PayPalPaymentSource()
    val requiredActionHandler: RequiredActionHandler = {
      Completable.complete()
    }

    every { SignalDatabase.inAppPayments.getById(inAppPayment.id) } returns inAppPayment

    val test = SharedInAppPaymentPipeline.awaitTransaction(
      inAppPayment.id,
      paymentSource,
      requiredActionHandler,
      requiredActionHandler
    ).test()

    test.assertNoErrors()

    updateDispatcher.onNext(
      inAppPayment.copy(
        state = InAppPaymentTable.State.PENDING
      )
    )

    rxRule.defaultScheduler.advanceTimeBy(10, TimeUnit.SECONDS)

    test.assertError {
      it is DonationError.BadgeRedemptionError.TimeoutWaitingForTokenError
    }
  }

  @Test
  fun `Given long-running PENDING state that times out, when I awaitTransaction, then I expect DonationPending`() {
    val inAppPayment = createInAppPayment(
      paymentMethodType = InAppPaymentData.PaymentMethodType.SEPA_DEBIT
    )

    val paymentSource = SEPADebitPaymentSource(
      sepaDebitData = StripeApi.SEPADebitData("", "", "")
    )

    val requiredActionHandler: RequiredActionHandler = {
      Completable.complete()
    }

    every { SignalDatabase.inAppPayments.getById(inAppPayment.id) } returns inAppPayment

    val test = SharedInAppPaymentPipeline.awaitTransaction(
      inAppPayment.id,
      paymentSource,
      requiredActionHandler,
      requiredActionHandler
    ).test()

    test.assertNoErrors()

    updateDispatcher.onNext(
      inAppPayment.copy(
        state = InAppPaymentTable.State.PENDING
      )
    )

    rxRule.defaultScheduler.advanceTimeBy(10, TimeUnit.SECONDS)

    test.assertError {
      it is DonationError.BadgeRedemptionError.DonationPending
    }
  }

  private fun createInAppPayment(
    type: InAppPaymentType = InAppPaymentType.RECURRING_DONATION,
    paymentMethodType: InAppPaymentData.PaymentMethodType = InAppPaymentData.PaymentMethodType.PAYPAL
  ): InAppPaymentTable.InAppPayment {
    return InAppPaymentTable.InAppPayment(
      id = InAppPaymentTable.InAppPaymentId(1L),
      type = type,
      state = InAppPaymentTable.State.CREATED,
      insertedAt = 0.milliseconds,
      updatedAt = 0.milliseconds,
      notified = true,
      subscriberId = null,
      endOfPeriod = 0.milliseconds,
      data = InAppPaymentData(
        paymentMethodType = paymentMethodType
      )
    )
  }
}
