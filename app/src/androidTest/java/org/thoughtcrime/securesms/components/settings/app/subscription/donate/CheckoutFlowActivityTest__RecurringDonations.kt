package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.deleteAll
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.permits.DonationPermits
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testing.GooglePayTestRule
import org.thoughtcrime.securesms.testing.InAppPaymentsRule
import org.thoughtcrime.securesms.testing.RxTestSchedulerRule
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.actions.scrollToDescendant
import org.thoughtcrime.securesms.testing.endpoints.DonationResponses
import org.thoughtcrime.securesms.testing.endpoints.MockEndpoints
import org.thoughtcrime.securesms.testing.endpoints.failure
import org.thoughtcrime.securesms.testing.endpoints.ok
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.util.Currency

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class CheckoutFlowActivityTest__RecurringDonations {
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

  private val intent = CheckoutFlowActivity.createIntent(InstrumentationRegistry.getInstrumentation().targetContext, InAppPaymentType.RECURRING_DONATION)

  @Before
  fun setUp() {
    SignalDatabase.inAppPayments.writableDatabase.deleteAll(InAppPaymentTable.TABLE_NAME)
    startJobLoopForTests()
  }

  @After
  fun tearDown() {
    unmockkObject(DonationPermits)
  }

  @Test
  fun givenRecurringDonations_whenILoadScreen_thenIExpectMonthlySelected() {
    ActivityScenario.launch<CheckoutFlowActivity>(intent)
    scrollToDescendant(R.id.recycler, withId(R.id.monthly), rxRule.defaultTestScheduler)
    onView(withId(R.id.monthly)).check(matches(isSelected()))
  }

  @Test
  fun givenNoCurrentDonation_whenILoadScreen_thenIExpectContinueButton() {
    ActivityScenario.launch<CheckoutFlowActivity>(intent)
    scrollToDescendant(R.id.recycler, withText(R.string.DonateToSignalFragment__continue), rxRule.defaultTestScheduler)
    onView(withText(R.string.DonateToSignalFragment__continue)).check(matches(isDisplayed()))
  }

  @Test
  fun givenACurrentDonation_whenILoadScreen_thenIExpectUpgradeButton() {
    initialiseActiveSubscription()

    ActivityScenario.launch<CheckoutFlowActivity>(intent)

    rxRule.defaultTestScheduler.triggerActions()

    scrollToDescendant(R.id.recycler, withText(R.string.SubscribeFragment__update_subscription), rxRule.defaultTestScheduler)
    onView(withText(R.string.SubscribeFragment__update_subscription)).check(matches(isDisplayed()))
    scrollToDescendant(R.id.recycler, withText(R.string.SubscribeFragment__cancel_subscription), rxRule.defaultTestScheduler)
    onView(withText(R.string.SubscribeFragment__cancel_subscription)).check(matches(isDisplayed()))
  }

  @Test
  fun givenACurrentDonation_whenIPressCancel_thenIExpectCancellationDialog() {
    initialiseActiveSubscription()

    ActivityScenario.launch<CheckoutFlowActivity>(intent)

    rxRule.defaultTestScheduler.triggerActions()

    scrollToDescendant(R.id.recycler, withText(R.string.SubscribeFragment__cancel_subscription), rxRule.defaultTestScheduler)
    onView(withText(R.string.SubscribeFragment__cancel_subscription)).check(matches(isDisplayed()))
    onView(withText(R.string.SubscribeFragment__cancel_subscription)).perform(ViewActions.click())
    onView(withText(R.string.SubscribeFragment__confirm_cancellation)).check(matches(isDisplayed()))
    onView(withText(R.string.SubscribeFragment__confirm)).perform(ViewActions.click())
  }

  @Test
  fun givenAPendingRecurringDonation_whenILoadScreen_thenIExpectDisabledUpgradeButton() {
    initialisePendingSubscription()

    ActivityScenario.launch<CheckoutFlowActivity>(intent)

    rxRule.defaultTestScheduler.triggerActions()

    scrollToDescendant(R.id.recycler, withText(R.string.SubscribeFragment__update_subscription), rxRule.defaultTestScheduler)
    onView(withText(R.string.SubscribeFragment__update_subscription)).check(matches(isDisplayed()))
    onView(withText(R.string.SubscribeFragment__update_subscription)).check(matches(isNotEnabled()))
  }

  @Test
  fun givenSubscriberPermitAcquisitionFails_whenISubscribe_thenIExpectPaymentSetupErrorDialog() {
    failDonationPermitAcquisition()

    val scenario = ActivityScenario.launch<CheckoutFlowActivity>(intent)
    rxRule.defaultTestScheduler.triggerActions()

    scrollToDescendant(R.id.recycler, withText(R.string.DonateToSignalFragment__continue), rxRule.defaultTestScheduler)
    onView(withText(R.string.DonateToSignalFragment__continue)).perform(ViewActions.click())
    rxRule.defaultTestScheduler.triggerActions()

    scenario.selectGooglePay(composeRule, rxRule.defaultTestScheduler, InAppPaymentType.RECURRING_DONATION)

    awaitDialog(rxRule.defaultTestScheduler, R.string.DonationsErrors__error_processing_payment)
    onView(withText(R.string.DonationsErrors__your_payment)).inRoot(isDialog()).check(matches(isDisplayed()))
  }

  @Test
  fun givenSubscriptionPaymentMethodPermitRejected_whenISubscribe_thenIExpectPaymentSetupErrorDialog() {
    succeedDonationPermitAcquisition()
    MockEndpoints.responder.register({ it.method == "POST" && it.path.contains("/create_payment_method") }) {
      failure(402)
    }

    val scenario = ActivityScenario.launch<CheckoutFlowActivity>(intent)
    rxRule.defaultTestScheduler.triggerActions()

    scrollToDescendant(R.id.recycler, withText(R.string.DonateToSignalFragment__continue), rxRule.defaultTestScheduler)
    onView(withText(R.string.DonateToSignalFragment__continue)).perform(ViewActions.click())
    rxRule.defaultTestScheduler.triggerActions()

    scenario.selectGooglePay(composeRule, rxRule.defaultTestScheduler, InAppPaymentType.RECURRING_DONATION)

    awaitDialog(rxRule.defaultTestScheduler, R.string.DonationsErrors__error_processing_payment)
    onView(withText(R.string.DonationsErrors__your_payment)).inRoot(isDialog()).check(matches(isDisplayed()))
  }

  private fun initialiseActiveSubscription() {
    val subscriber = registerSubscriber()
    val serialized = subscriber.subscriberId.serialize()
    MockEndpoints.responder.register({ it.method == "GET" && it.path.contains(serialized) }) {
      ok(DonationResponses.activeSubscription(status = "active", active = true))
    }
  }

  private fun initialisePendingSubscription() {
    val subscriber = registerSubscriber()
    val serialized = subscriber.subscriberId.serialize()
    MockEndpoints.responder.register({ it.method == "GET" && it.path.contains(serialized) }) {
      ok(DonationResponses.activeSubscription(status = "incomplete", active = false))
    }
  }

  private fun registerSubscriber(): InAppPaymentSubscriberRecord {
    val currency = Currency.getInstance("USD")
    val subscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = currency,
      type = InAppPaymentSubscriberRecord.Type.DONATION,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.CARD,
      iapSubscriptionId = null
    )

    InAppPaymentsRepository.setSubscriber(subscriber)
    SignalStore.inAppPayments.setRecurringDonationCurrency(currency)

    return subscriber
  }
}
