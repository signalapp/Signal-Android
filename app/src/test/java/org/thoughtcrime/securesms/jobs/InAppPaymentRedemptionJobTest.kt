/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.every
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsTestRule
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.whispersystems.signalservice.api.subscriptions.SubscriberId

class InAppPaymentRedemptionJobTest {

  @get:Rule
  val mockSignalStore = MockSignalStoreRule()

  @get:Rule
  val inAppPaymentsTestRule = InAppPaymentsTestRule()

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())
  }

  @Test
  fun `Given an unregistered local user, when I run, then I expect failure`() {
    every { mockSignalStore.account.isRegistered } returns false

    val job = InAppPaymentRedemptionJob.create()

    val result = job.run()

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `Given a PENDING IAP without an error, when I onFailure, then I set a redemption error and move to end state`() {
    val iap = insertInAppPayment(
      state = InAppPaymentTable.State.PENDING,
      redemptionState = InAppPaymentData.RedemptionState(stage = InAppPaymentData.RedemptionState.Stage.CONVERSION_STARTED)
    )
    val job = InAppPaymentRedemptionJob.create(iap)

    job.onFailure()
    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)

    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(updatedIap?.data?.error).isNotNull()
    assertThat(updatedIap?.data?.error?.type).isEqualTo(InAppPaymentData.Error.Type.REDEMPTION)
    assertThat(updatedIap?.notified).isEqualTo(false)
  }

  @Test
  fun `Given an already redeemed IAP, when I onFailure, then I do not set an error`() {
    val iap = insertInAppPayment(
      state = InAppPaymentTable.State.END,
      redemptionState = InAppPaymentData.RedemptionState(stage = InAppPaymentData.RedemptionState.Stage.REDEEMED)
    )
    val job = InAppPaymentRedemptionJob.create(iap)

    job.onFailure()
    val updatedIap = SignalDatabase.inAppPayments.getById(iap.id)

    assertThat(updatedIap?.state).isEqualTo(InAppPaymentTable.State.END)
    assertThat(updatedIap?.data?.error).isNull()
    assertThat(updatedIap?.data?.redemption?.stage).isEqualTo(InAppPaymentData.RedemptionState.Stage.REDEEMED)
    assertThat(updatedIap?.notified).isEqualTo(true)
  }

  private fun insertInAppPayment(
    state: InAppPaymentTable.State,
    redemptionState: InAppPaymentData.RedemptionState
  ): InAppPaymentTable.InAppPayment {
    val iap = inAppPaymentsTestRule.createInAppPayment(InAppPaymentType.RECURRING_DONATION, PaymentSourceType.Stripe.SEPADebit)
    SignalDatabase.inAppPayments.insert(
      type = iap.type,
      state = state,
      subscriberId = SubscriberId.generate(),
      endOfPeriod = null,
      inAppPaymentData = iap.data.newBuilder()
        .redemption(redemptionState)
        .build()
    )

    return iap
  }
}
