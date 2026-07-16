/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.mockk.every
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.deleteAll
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.database.InAppPaymentSubscriberTable
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.math.BigDecimal
import java.util.Currency
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class InAppPaymentsRepositoryTest {

  @get:Rule
  val signalStore = MockSignalStoreRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

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
