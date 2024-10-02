package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.MockResponse
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.InstrumentationApplicationDependencyProvider
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testing.Delete
import org.thoughtcrime.securesms.testing.Get
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.success
import org.thoughtcrime.securesms.util.JsonUtils
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import java.math.BigDecimal
import java.util.Currency
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

@Ignore("Test fails on small screens, requires scrolling.")
@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class CheckoutFlowActivityTest__RecurringDonations {
  @get:Rule
  val harness = SignalActivityRule(othersCount = 10)

  private val intent = CheckoutFlowActivity.createIntent(InstrumentationRegistry.getInstrumentation().targetContext, InAppPaymentType.RECURRING_DONATION)

  @Test
  fun givenRecurringDonations_whenILoadScreen_thenIExpectMonthlySelected() {
    ActivityScenario.launch<CheckoutFlowActivity>(intent)
    onView(withId(R.id.monthly)).check(matches(isSelected()))
  }

  @Test
  fun givenNoCurrentDonation_whenILoadScreen_thenIExpectContinueButton() {
    ActivityScenario.launch<CheckoutFlowActivity>(intent)
    onView(withText("Continue")).check(matches(isDisplayed()))
  }

  @Test
  fun givenACurrentDonation_whenILoadScreen_thenIExpectUpgradeButton() {
    initialiseConfigurationResponse()
    initialiseActiveSubscription()

    ActivityScenario.launch<CheckoutFlowActivity>(intent)
    onView(withText(R.string.SubscribeFragment__update_subscription)).check(matches(isDisplayed()))
    onView(withText(R.string.SubscribeFragment__cancel_subscription)).check(matches(isDisplayed()))
  }

  @Test
  fun givenACurrentDonation_whenIPressCancel_thenIExpectCancellationDialog() {
    initialiseConfigurationResponse()
    initialiseActiveSubscription()

    ActivityScenario.launch<CheckoutFlowActivity>(intent)
    onView(withText(R.string.SubscribeFragment__cancel_subscription)).check(matches(isDisplayed()))
    onView(withText(R.string.SubscribeFragment__cancel_subscription)).perform(ViewActions.click())
    onView(withText(R.string.SubscribeFragment__confirm_cancellation)).check(matches(isDisplayed()))
    onView(withText(R.string.SubscribeFragment__confirm)).perform(ViewActions.click())
    onView(withText(R.string.StripePaymentInProgressFragment__cancelling)).check(matches(isDisplayed()))
  }

  @Test
  fun givenAPendingRecurringDonation_whenILoadScreen_thenIExpectDisabledUpgradeButton() {
    initialiseConfigurationResponse()
    initialisePendingSubscription()

    ActivityScenario.launch<CheckoutFlowActivity>(intent)
    onView(withText(R.string.SubscribeFragment__update_subscription)).check(matches(isDisplayed()))
    onView(withText(R.string.SubscribeFragment__update_subscription)).check(matches(isNotEnabled()))
  }

  private fun initialiseConfigurationResponse() {
    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Get("/v1/subscription/configuration") {
        val assets = InstrumentationRegistry.getInstrumentation().context.resources.assets
        assets.open("inAppPaymentsTests/configuration.json").use { stream ->
          MockResponse().success(JsonUtils.fromJson(stream, SubscriptionsConfiguration::class.java))
        }
      }
    )
  }

  private fun initialiseActiveSubscription() {
    val currency = Currency.getInstance("USD")
    val subscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = currency,
      type = InAppPaymentSubscriberRecord.Type.DONATION,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.CARD
    )

    InAppPaymentsRepository.setSubscriber(subscriber)
    SignalStore.inAppPayments.setSubscriberCurrency(currency, subscriber.type)

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Get("/v1/subscription/${subscriber.subscriberId.serialize()}") {
        MockResponse().success(
          ActiveSubscription(
            ActiveSubscription.Subscription(
              200,
              currency.currencyCode,
              BigDecimal.ONE,
              System.currentTimeMillis().milliseconds.inWholeSeconds + 30.days.inWholeSeconds,
              true,
              System.currentTimeMillis().milliseconds.inWholeSeconds + 30.days.inWholeSeconds,
              false,
              "active",
              "STRIPE",
              "CARD",
              false
            ),
            null
          )
        )
      },
      Delete("/v1/subscription/${subscriber.subscriberId.serialize()}") {
        Thread.sleep(10000)
        MockResponse().success()
      }
    )
  }

  private fun initialisePendingSubscription() {
    val currency = Currency.getInstance("USD")
    val subscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = currency,
      type = InAppPaymentSubscriberRecord.Type.DONATION,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.CARD
    )

    InAppPaymentsRepository.setSubscriber(subscriber)
    SignalStore.inAppPayments.setSubscriberCurrency(currency, subscriber.type)

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Get("/v1/subscription/${subscriber.subscriberId.serialize()}") {
        MockResponse().success(
          ActiveSubscription(
            ActiveSubscription.Subscription(
              200,
              currency.currencyCode,
              BigDecimal.ONE,
              System.currentTimeMillis().milliseconds.inWholeSeconds + 30.days.inWholeSeconds,
              false,
              System.currentTimeMillis().milliseconds.inWholeSeconds + 30.days.inWholeSeconds,
              false,
              "incomplete",
              "STRIPE",
              "CARD",
              false
            ),
            null
          )
        )
      }
    )
  }
}
