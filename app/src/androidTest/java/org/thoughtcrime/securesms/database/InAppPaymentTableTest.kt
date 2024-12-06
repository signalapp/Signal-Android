/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.deleteAll
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.assertIs

@RunWith(AndroidJUnit4::class)
class InAppPaymentTableTest {
  @get:Rule
  val harness = SignalActivityRule()

  @Before
  fun setUp() {
    SignalDatabase.inAppPayments.writableDatabase.deleteAll(InAppPaymentTable.TABLE_NAME)
  }

  @Test
  fun givenACreatedInAppPayment_whenIUpdateToPending_thenIExpectPendingPayment() {
    val inAppPaymentId = SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.ONE_TIME_DONATION,
      state = InAppPaymentTable.State.CREATED,
      subscriberId = null,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData()
    )

    val paymentBeforeUpdate = SignalDatabase.inAppPayments.getById(inAppPaymentId)
    paymentBeforeUpdate?.state assertIs InAppPaymentTable.State.CREATED

    SignalDatabase.inAppPayments.update(
      inAppPayment = paymentBeforeUpdate!!.copy(state = InAppPaymentTable.State.PENDING)
    )

    val paymentAfterUpdate = SignalDatabase.inAppPayments.getById(inAppPaymentId)
    paymentAfterUpdate?.state assertIs InAppPaymentTable.State.PENDING
  }
}
