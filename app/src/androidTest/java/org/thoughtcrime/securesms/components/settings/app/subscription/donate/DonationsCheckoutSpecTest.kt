package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.signal.core.util.deleteAll
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.permits.DonationPermits
import org.thoughtcrime.securesms.database.DonationReceiptTable
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.testing.GooglePayTestRule
import org.thoughtcrime.securesms.testing.InAppPaymentsRule
import org.thoughtcrime.securesms.testing.RawFlag
import org.thoughtcrime.securesms.testing.RemoteConfigForTest
import org.thoughtcrime.securesms.testing.RxTestSchedulerRule
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.flushUntil

/**
 * Spec-driven integration test for donation checkout. Each [DonationsCheckoutTestSpec] declares a
 * "world" (what each endpoint returns) and an expected outcome; this runner applies the world, drives
 * [CheckoutFlowActivity] through the real UI via [CheckoutFlowDriver], and asserts the outcome.
 *
 * Adding coverage is data: append a spec to [DonationsCheckoutSpecs.ALL].
 */
@RunWith(Parameterized::class)
@RemoteConfigForTest(
  rawFlags = [
    RawFlag("android.sepa.debit.donations.5", "true"),
    RawFlag("android.ideal.donations.5", "true"),
    // Region codes are matched against the digit-only self E164 ("15555550101"), so no leading "+".
    RawFlag("global.donations.sepaEnabledRegions", "1"),
    RawFlag("global.donations.idealEnabledRegions", "1")
  ]
)
class DonationsCheckoutSpecTest(private val spec: DonationsCheckoutTestSpec) {

  @get:Rule
  val harness = SignalActivityRule(othersCount = 10)

  @get:Rule
  val iapRule = InAppPaymentsRule()

  @get:Rule
  val rxRule = RxTestSchedulerRule()

  @get:Rule
  val googlePayRule = GooglePayTestRule()

  @get:Rule
  val composeRule = createEmptyComposeRule()

  @Before
  fun setUp() {
    SignalDatabase.inAppPayments.writableDatabase.deleteAll(InAppPaymentTable.TABLE_NAME)
    SignalDatabase.donationReceipts.writableDatabase.deleteAll(DonationReceiptTable.TABLE_NAME)
    startJobLoopForTests()
    spec.world.apply()
  }

  @After
  fun tearDown() {
    unmockkObject(DonationPermits)
  }

  @Test
  fun runSpec() {
    val intent = CheckoutFlowActivity.createIntent(InstrumentationRegistry.getInstrumentation().targetContext, spec.inAppPaymentType)
    val scenario = ActivityScenario.launch<CheckoutFlowActivity>(intent)

    CheckoutFlowDriver(composeRule, rxRule.defaultTestScheduler).drive(scenario, spec)

    when (val expected = spec.expected) {
      is ExpectedOutcome.Redeemed -> {
        rxRule.defaultTestScheduler.flushUntil { receipts().isNotEmpty() }
        assertThat(receipts()).isNotEmpty()
      }

      is ExpectedOutcome.ErrorDialog -> {
        awaitDialog(rxRule.defaultTestScheduler, expected.titleRes)
        if (expected.messageRes != null) {
          onView(withText(expected.messageRes)).inRoot(isDialog()).check(matches(isDisplayed()))
        }
      }

      is ExpectedOutcome.PaymentSubmitted -> {
        rxRule.defaultTestScheduler.flushUntil { submittedPayment() != null }
        val payment = submittedPayment()
        assertThat(payment).isNotNull()
        assertThat(payment!!.data.error).isNull()
      }
    }
  }

  /** The latest payment once it has moved past CREATED with no error, or null if not yet there. */
  private fun submittedPayment(): InAppPaymentTable.InAppPayment? {
    return SignalDatabase.inAppPayments.getLatestInAppPaymentByType(spec.inAppPaymentType)
      ?.takeIf { it.state != InAppPaymentTable.State.CREATED && it.data.error == null }
  }

  private fun receipts(): List<InAppPaymentReceiptRecord> {
    val type = if (spec.inAppPaymentType == InAppPaymentType.ONE_TIME_DONATION) {
      InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION
    } else {
      InAppPaymentReceiptRecord.Type.RECURRING_DONATION
    }
    return SignalDatabase.donationReceipts.getReceipts(type)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun specs(): Collection<DonationsCheckoutTestSpec> = DonationsCheckoutSpecs.ALL
  }
}

/**
 * The enumerated donation checkout scenarios. Currently seeded with Google Pay coverage; the other
 * payment-method drivers (card, SEPA, iDEAL, PayPal) and their scenarios are added alongside their
 * [CheckoutFlowDriver] strategies.
 */
object DonationsCheckoutSpecs {

  private val paymentErrorDialog = ExpectedOutcome.ErrorDialog(
    titleRes = R.string.DonationsErrors__error_processing_payment,
    messageRes = R.string.DonationsErrors__your_payment
  )

  val ALL: List<DonationsCheckoutTestSpec> = listOf(
    DonationsCheckoutTestSpec(
      name = "googlePay_oneTime_happyPath_isRedeemed",
      inAppPaymentType = InAppPaymentType.ONE_TIME_DONATION,
      paymentMethod = TestPaymentMethod.GOOGLE_PAY,
      world = WorldState(),
      expected = ExpectedOutcome.Redeemed
    ),
    DonationsCheckoutTestSpec(
      name = "googlePay_oneTime_permitFailure_showsError",
      inAppPaymentType = InAppPaymentType.ONE_TIME_DONATION,
      paymentMethod = TestPaymentMethod.GOOGLE_PAY,
      world = WorldState(permit = PermitOutcome.FAILURE),
      expected = paymentErrorDialog
    ),
    DonationsCheckoutTestSpec(
      name = "googlePay_oneTime_stripeDecline_showsError",
      inAppPaymentType = InAppPaymentType.ONE_TIME_DONATION,
      paymentMethod = TestPaymentMethod.GOOGLE_PAY,
      world = WorldState(stripe = StripeOutcome.DECLINE),
      expected = paymentErrorDialog
    ),
    DonationsCheckoutTestSpec(
      name = "googlePay_oneTime_signalCreateIntent402_showsError",
      inAppPaymentType = InAppPaymentType.ONE_TIME_DONATION,
      paymentMethod = TestPaymentMethod.GOOGLE_PAY,
      world = WorldState(signalCreatePaymentStatus = 402),
      expected = paymentErrorDialog
    ),
    DonationsCheckoutTestSpec(
      name = "googlePay_recurring_permitFailure_showsError",
      inAppPaymentType = InAppPaymentType.RECURRING_DONATION,
      paymentMethod = TestPaymentMethod.GOOGLE_PAY,
      world = WorldState(permit = PermitOutcome.FAILURE),
      expected = paymentErrorDialog
    ),
    DonationsCheckoutTestSpec(
      name = "creditCard_oneTime_happyPath_isRedeemed",
      inAppPaymentType = InAppPaymentType.ONE_TIME_DONATION,
      paymentMethod = TestPaymentMethod.CREDIT_CARD,
      world = WorldState(),
      expected = ExpectedOutcome.Redeemed
    ),
    DonationsCheckoutTestSpec(
      name = "creditCard_oneTime_stripeDecline_showsError",
      inAppPaymentType = InAppPaymentType.ONE_TIME_DONATION,
      paymentMethod = TestPaymentMethod.CREDIT_CARD,
      world = WorldState(stripe = StripeOutcome.DECLINE),
      expected = paymentErrorDialog
    ),
    DonationsCheckoutTestSpec(
      name = "creditCard_oneTime_3dsApproved_isRedeemed",
      inAppPaymentType = InAppPaymentType.ONE_TIME_DONATION,
      paymentMethod = TestPaymentMethod.CREDIT_CARD,
      world = WorldState(stripe = StripeOutcome.REQUIRES_3DS),
      expected = ExpectedOutcome.Redeemed
    ),
    DonationsCheckoutTestSpec(
      name = "payPal_oneTime_happyPath_isRedeemed",
      inAppPaymentType = InAppPaymentType.ONE_TIME_DONATION,
      paymentMethod = TestPaymentMethod.PAYPAL,
      world = WorldState(),
      expected = ExpectedOutcome.Redeemed
    ),
    DonationsCheckoutTestSpec(
      name = "sepa_oneTime_happyPath_isSubmitted",
      inAppPaymentType = InAppPaymentType.ONE_TIME_DONATION,
      paymentMethod = TestPaymentMethod.SEPA_DEBIT,
      world = WorldState(currencyCode = "EUR"),
      expected = ExpectedOutcome.PaymentSubmitted
    ),
    DonationsCheckoutTestSpec(
      name = "ideal_oneTime_happyPath_isSubmitted",
      inAppPaymentType = InAppPaymentType.ONE_TIME_DONATION,
      paymentMethod = TestPaymentMethod.IDEAL,
      world = WorldState(currencyCode = "EUR"),
      expected = ExpectedOutcome.PaymentSubmitted
    ),

    // Recurring happy paths. Asserted as "submitted": the recurring-specific work is the setup
    // pipeline (create subscriber, setup intent, set default, set level); receipt redemption is
    // shared with the one-time path already covered above.
    DonationsCheckoutTestSpec(
      name = "googlePay_recurring_happyPath_isSubmitted",
      inAppPaymentType = InAppPaymentType.RECURRING_DONATION,
      paymentMethod = TestPaymentMethod.GOOGLE_PAY,
      world = WorldState(recurringActivatesAfterSetup = true),
      expected = ExpectedOutcome.PaymentSubmitted
    ),
    DonationsCheckoutTestSpec(
      name = "creditCard_recurring_happyPath_isSubmitted",
      inAppPaymentType = InAppPaymentType.RECURRING_DONATION,
      paymentMethod = TestPaymentMethod.CREDIT_CARD,
      world = WorldState(recurringActivatesAfterSetup = true),
      expected = ExpectedOutcome.PaymentSubmitted
    ),
    DonationsCheckoutTestSpec(
      name = "payPal_recurring_happyPath_isSubmitted",
      inAppPaymentType = InAppPaymentType.RECURRING_DONATION,
      paymentMethod = TestPaymentMethod.PAYPAL,
      world = WorldState(recurringActivatesAfterSetup = true),
      expected = ExpectedOutcome.PaymentSubmitted
    ),
    DonationsCheckoutTestSpec(
      name = "sepa_recurring_happyPath_isSubmitted",
      inAppPaymentType = InAppPaymentType.RECURRING_DONATION,
      paymentMethod = TestPaymentMethod.SEPA_DEBIT,
      world = WorldState(currencyCode = "EUR"),
      expected = ExpectedOutcome.PaymentSubmitted
    ),

    // Additional error cases.
    DonationsCheckoutTestSpec(
      name = "creditCard_oneTime_permitFailure_showsError",
      inAppPaymentType = InAppPaymentType.ONE_TIME_DONATION,
      paymentMethod = TestPaymentMethod.CREDIT_CARD,
      world = WorldState(permit = PermitOutcome.FAILURE),
      expected = paymentErrorDialog
    ),
    DonationsCheckoutTestSpec(
      name = "creditCard_recurring_stripeDecline_showsError",
      inAppPaymentType = InAppPaymentType.RECURRING_DONATION,
      paymentMethod = TestPaymentMethod.CREDIT_CARD,
      world = WorldState(stripe = StripeOutcome.DECLINE),
      expected = paymentErrorDialog
    ),
    DonationsCheckoutTestSpec(
      name = "googlePay_recurring_signalCreatePaymentMethod402_showsError",
      inAppPaymentType = InAppPaymentType.RECURRING_DONATION,
      paymentMethod = TestPaymentMethod.GOOGLE_PAY,
      world = WorldState(signalCreatePaymentStatus = 402),
      expected = paymentErrorDialog
    )
  )
}
