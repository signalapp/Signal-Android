/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import io.mockk.every
import io.mockk.mockkObject
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.signal.donations.InAppPaymentType
import org.signal.libsignal.net.RequestResult
import org.signal.network.rest.RestStatusCodeError
import org.thoughtcrime.securesms.SignalInstrumentationApplicationContext
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewaySelectorTestTags
import org.thoughtcrime.securesms.components.settings.app.subscription.permits.DonationPermits
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testing.flushUntil

/**
 * Forces donation-permit acquisition to fail, so the checkout surfaces a payment-setup error before
 * reaching the payment method. Stubs the [DonationPermits] object directly (the underlying
 * `createDonationPermits` call rides the websocket, which the responder does not mint valid permits
 * for). Call `unmockkObject(DonationPermits)` in teardown.
 */
fun failDonationPermitAcquisition(statusCode: Int = 500) {
  AppDependencies.donationPermitsRepository.clearPermits()
  mockkObject(DonationPermits)
  every { DonationPermits.getDonationPermit() } returns RequestResult.NonSuccess(RestStatusCodeError(statusCode, emptyMap(), null))
}

/**
 * Makes donation-permit acquisition succeed with a placeholder permit, letting the checkout proceed
 * past the permit gate to the payment method. Call `unmockkObject(DonationPermits)` in teardown.
 */
fun succeedDonationPermitAcquisition() {
  AppDependencies.donationPermitsRepository.clearPermits()
  mockkObject(DonationPermits)
  every { DonationPermits.getDonationPermit() } returns RequestResult.Success("permit")
}

/**
 * The instrumentation app stubs [org.thoughtcrime.securesms.ApplicationContext.beginJobLoop] to a no-op, so enqueued
 * jobs never run. The checkout pipeline drives a real setup job through the JobManager, so the loop must be started.
 *
 * Before starting it, cancel any leftover in-app-payment jobs. A recurring setup job retries indefinitely over a
 * one-day lifespan, so a spec that fails setup leaves one persisted in the JobManager. On Firebase Test Lab the
 * orchestrator's `clearPackageData` does not reliably wipe the JobManager between parameterized specs (it does on a
 * local emulator, which is why this only surfaces on-device), so that job survives into the next spec and runs after
 * its InAppPayment row was deleted in setup — `InAppPaymentsRepository.resolveLock` then throws "Not found" from the
 * job thread and crashes the whole app. Clearing the queues here makes each test start from a clean job state.
 */
fun startJobLoopForTests() {
  AppDependencies.jobManager.cancelAllInQueues(
    setOf(
      InAppPaymentsRepository.getRecurringJobQueueKey(InAppPaymentType.RECURRING_DONATION),
      InAppPaymentsRepository.getRecurringJobQueueKey(InAppPaymentType.RECURRING_BACKUP)
    )
  )
  AppDependencies.jobManager.flush()

  (AppDependencies.application as SignalInstrumentationApplicationContext).beginJobLoopForTests()
}

/**
 * Selects Google Pay in the Compose gateway selector and feeds the stubbed Google Pay result back into the
 * checkout, navigating to the payment-in-progress screen where the pipeline runs.
 *
 * The gateway selector is populated by Rx work on [scheduler], so we [flushUntil] the button is present
 * rather than sleeping. Selecting Google Pay dismisses the gateway sheet and hands a fragment result back to
 * the checkout, which runs `launchGooglePay` -> `provideGatewayRequestForGooglePay` on [scheduler]; only then
 * does the checkout's subscriber consume a [GooglePayComponent.googlePayResultPublisher] emission
 * (`consumeGatewayRequestForGooglePay` returns null until then, and the publisher is a hot PublishSubject that
 * drops earlier emissions). So we [flushUntil] the sheet has dismissed — a real signal that the click was
 * fully processed — before dispatching the result, rather than pumping a fixed number of times and racing.
 */
fun ActivityScenario<CheckoutFlowActivity>.selectGooglePay(
  composeRule: ComposeTestRule,
  scheduler: TestScheduler,
  inAppPaymentType: InAppPaymentType
) {
  scheduler.flushUntil {
    composeRule.onAllNodesWithTag(GatewaySelectorTestTags.GOOGLE_PAY_BUTTON).fetchSemanticsNodes().isNotEmpty()
  }

  composeRule.onNodeWithTag(GatewaySelectorTestTags.CONTAINER).performScrollToNode(hasTestTag(GatewaySelectorTestTags.GOOGLE_PAY_BUTTON))
  composeRule.onNodeWithTag(GatewaySelectorTestTags.GOOGLE_PAY_BUTTON).performClick()

  scheduler.flushUntil {
    // Once the gateway sheet dismisses there is no Compose hierarchy left, so fetchSemanticsNodes throws
    // rather than returning empty; treat both the empty list and the missing hierarchy as "sheet gone".
    runCatching {
      composeRule.onAllNodesWithTag(GatewaySelectorTestTags.GOOGLE_PAY_BUTTON).fetchSemanticsNodes().isEmpty()
    }.getOrDefault(true)
  }

  onActivity { activity ->
    (activity as GooglePayComponent).googlePayResultPublisher.onNext(
      GooglePayComponent.GooglePayResult(
        requestCode = InAppPaymentsRepository.getGooglePayRequestCode(inAppPaymentType),
        resultCode = Activity.RESULT_OK,
        data = Intent()
      )
    )
  }
}

/**
 * Waits for a dialog whose title is [titleResId] to be displayed. Matches any dialog by title (error,
 * confirmation, thanks, etc.) — it is not specific to error dialogs.
 *
 * [flushUntil] advances the Rx pipeline (which may create the payment, enqueue the real setup job, and react
 * to its committed state) while yielding real time for any job to run, until the dialog renders. A single
 * condition-driven pump rather than a fixed-duration wall-clock poll. Letting the Espresso check throw (rather
 * than collapsing it to a boolean) lets [flushUntil] chain the last matcher failure as the timeout cause.
 */
fun awaitDialog(
  scheduler: TestScheduler,
  titleResId: Int,
  timeoutMillis: Long = 15_000
) {
  scheduler.flushUntil(timeoutMillis) {
    onView(withText(titleResId)).inRoot(isDialog()).check(matches(isDisplayed()))
    true
  }
}
