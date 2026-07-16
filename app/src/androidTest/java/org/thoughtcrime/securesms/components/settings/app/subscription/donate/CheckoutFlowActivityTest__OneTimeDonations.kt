/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.unmockkObject
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.deleteAll
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.permits.DonationPermits
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testing.GooglePayTestRule
import org.thoughtcrime.securesms.testing.InAppPaymentsRule
import org.thoughtcrime.securesms.testing.RxTestSchedulerRule
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.actions.scrollToDescendant

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class CheckoutFlowActivityTest__OneTimeDonations {
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

  private val intent = CheckoutFlowActivity.createIntent(InstrumentationRegistry.getInstrumentation().targetContext, InAppPaymentType.ONE_TIME_DONATION)

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
  fun givenPermitAcquisitionFails_whenIDonateOnce_thenIExpectPaymentSetupErrorDialog() {
    failDonationPermitAcquisition()

    val scenario = ActivityScenario.launch<CheckoutFlowActivity>(intent)
    rxRule.defaultTestScheduler.triggerActions()

    scrollToDescendant(R.id.recycler, withId(R.id.boost_1), rxRule.defaultTestScheduler)
    onView(allOf(withId(R.id.boost_1), isClickable())).perform(ViewActions.click())
    rxRule.defaultTestScheduler.triggerActions()

    scrollToDescendant(R.id.recycler, withText(R.string.DonateToSignalFragment__continue), rxRule.defaultTestScheduler)
    onView(withText(R.string.DonateToSignalFragment__continue)).perform(ViewActions.click())
    rxRule.defaultTestScheduler.triggerActions()

    scenario.selectGooglePay(composeRule, rxRule.defaultTestScheduler, InAppPaymentType.ONE_TIME_DONATION)

    awaitDialog(rxRule.defaultTestScheduler, R.string.DonationsErrors__error_processing_payment)
    onView(withText(R.string.DonationsErrors__your_payment)).inRoot(isDialog()).check(matches(isDisplayed()))
  }
}
