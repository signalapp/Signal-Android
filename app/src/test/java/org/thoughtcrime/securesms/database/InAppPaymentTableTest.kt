/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.single
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.deleteAll
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.jobs.InAppPaymentKeepAliveJob
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class InAppPaymentTableTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

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
    assertThat(paymentBeforeUpdate?.state).isEqualTo(InAppPaymentTable.State.CREATED)

    SignalDatabase.inAppPayments.update(
      inAppPayment = paymentBeforeUpdate!!.copy(state = InAppPaymentTable.State.PENDING)
    )

    val paymentAfterUpdate = SignalDatabase.inAppPayments.getById(inAppPaymentId)
    assertThat(paymentAfterUpdate?.state).isEqualTo(InAppPaymentTable.State.PENDING)
  }

  // region consumeDonationPaymentsToNotifyUser

  @Test
  fun `consumeDonationPaymentsToNotifyUser when table is empty, returns empty list`() {
    val result = SignalDatabase.inAppPayments.consumeDonationPaymentsToNotifyUser()
    assertThat(result).isEmpty()
  }

  @Test
  fun `consumeDonationPaymentsToNotifyUser when only already-notified donations exist, returns empty list`() {
    insertDonation(notified = true)

    val result = SignalDatabase.inAppPayments.consumeDonationPaymentsToNotifyUser()
    assertThat(result).isEmpty()
  }

  @Test
  fun `consumeDonationPaymentsToNotifyUser when unnotified donation exists, returns it`() {
    val id = insertDonation(notified = false)

    val result = SignalDatabase.inAppPayments.consumeDonationPaymentsToNotifyUser()
    assertThat(result).single().transform { it.id }.isEqualTo(id)
  }

  @Test
  fun `consumeDonationPaymentsToNotifyUser marks returned payments as notified`() {
    insertDonation(notified = false)

    SignalDatabase.inAppPayments.consumeDonationPaymentsToNotifyUser()

    val secondCall = SignalDatabase.inAppPayments.consumeDonationPaymentsToNotifyUser()
    assertThat(secondCall).isEmpty()
  }

  @Test
  fun `consumeDonationPaymentsToNotifyUser does not return backup payments`() {
    insertBackup(notified = false)

    val result = SignalDatabase.inAppPayments.consumeDonationPaymentsToNotifyUser()
    assertThat(result).isEmpty()
  }

  @Test
  fun `consumeDonationPaymentsToNotifyUser does not return or mark in-flight donations`() {
    val createdId = insertDonation(notified = false, state = InAppPaymentTable.State.CREATED)
    val transactingId = insertDonation(notified = false, state = InAppPaymentTable.State.TRANSACTING)
    val waitingId = insertDonation(notified = false, state = InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION)
    val pendingId = insertDonation(notified = false, state = InAppPaymentTable.State.PENDING)

    val result = SignalDatabase.inAppPayments.consumeDonationPaymentsToNotifyUser()

    assertThat(result).isEmpty()
    // Must remain unnotified so the terminal sheet can still be shown once they reach a notifiable state.
    assertThat(SignalDatabase.inAppPayments.getById(createdId)!!.notified).isEqualTo(false)
    assertThat(SignalDatabase.inAppPayments.getById(transactingId)!!.notified).isEqualTo(false)
    assertThat(SignalDatabase.inAppPayments.getById(waitingId)!!.notified).isEqualTo(false)
    assertThat(SignalDatabase.inAppPayments.getById(pendingId)!!.notified).isEqualTo(false)
  }

  @Test
  fun `consumeDonationPaymentsToNotifyUser returns an in-flight donation only once it reaches END`() {
    val id = insertDonation(notified = false, state = InAppPaymentTable.State.PENDING)

    assertThat(SignalDatabase.inAppPayments.consumeDonationPaymentsToNotifyUser()).isEmpty()

    val pending = SignalDatabase.inAppPayments.getById(id)!!
    SignalDatabase.inAppPayments.update(pending.copy(state = InAppPaymentTable.State.END))

    val result = SignalDatabase.inAppPayments.consumeDonationPaymentsToNotifyUser()
    assertThat(result).single().transform { it.id }.isEqualTo(id)
  }

  @Test
  fun `consumeDonationPaymentsToNotifyUser returns a pending donation that carries a keep-alive error`() {
    val id = insertDonation(notified = false, state = InAppPaymentTable.State.PENDING, data = keepAliveErrorData())

    val result = SignalDatabase.inAppPayments.consumeDonationPaymentsToNotifyUser()
    assertThat(result).single().transform { it.id }.isEqualTo(id)
  }

  // endregion

  // region consumeBackupPaymentsToNotifyUser

  @Test
  fun `consumeBackupPaymentsToNotifyUser when table is empty, returns empty list`() {
    val result = SignalDatabase.inAppPayments.consumeBackupPaymentsToNotifyUser()
    assertThat(result).isEmpty()
  }

  @Test
  fun `consumeBackupPaymentsToNotifyUser when only already-notified backups exist, returns empty list`() {
    insertBackup(notified = true)

    val result = SignalDatabase.inAppPayments.consumeBackupPaymentsToNotifyUser()
    assertThat(result).isEmpty()
  }

  @Test
  fun `consumeBackupPaymentsToNotifyUser when unnotified backup exists, returns it`() {
    val id = insertBackup(notified = false)

    val result = SignalDatabase.inAppPayments.consumeBackupPaymentsToNotifyUser()
    assertThat(result).single().transform { it.id }.isEqualTo(id)
  }

  @Test
  fun `consumeBackupPaymentsToNotifyUser marks returned payments as notified`() {
    insertBackup(notified = false)

    SignalDatabase.inAppPayments.consumeBackupPaymentsToNotifyUser()

    val secondCall = SignalDatabase.inAppPayments.consumeBackupPaymentsToNotifyUser()
    assertThat(secondCall).isEmpty()
  }

  @Test
  fun `consumeBackupPaymentsToNotifyUser does not return donation payments`() {
    insertDonation(notified = false)

    val result = SignalDatabase.inAppPayments.consumeBackupPaymentsToNotifyUser()
    assertThat(result).isEmpty()
  }

  @Test
  fun `consumeBackupPaymentsToNotifyUser does not return or mark in-flight backups`() {
    val id = insertBackup(notified = false, state = InAppPaymentTable.State.PENDING)

    assertThat(SignalDatabase.inAppPayments.consumeBackupPaymentsToNotifyUser()).isEmpty()
    assertThat(SignalDatabase.inAppPayments.getById(id)!!.notified).isEqualTo(false)
  }

  // endregion

  // region helpers

  private fun insertDonation(
    notified: Boolean,
    state: InAppPaymentTable.State = InAppPaymentTable.State.END,
    data: InAppPaymentData = InAppPaymentData()
  ): InAppPaymentTable.InAppPaymentId = insertPayment(type = InAppPaymentType.ONE_TIME_DONATION, notified = notified, state = state, data = data)

  private fun insertBackup(
    notified: Boolean,
    state: InAppPaymentTable.State = InAppPaymentTable.State.END,
    data: InAppPaymentData = InAppPaymentData()
  ): InAppPaymentTable.InAppPaymentId = insertPayment(type = InAppPaymentType.RECURRING_BACKUP, notified = notified, state = state, data = data)

  private fun insertPayment(
    type: InAppPaymentType,
    notified: Boolean,
    state: InAppPaymentTable.State,
    data: InAppPaymentData
  ): InAppPaymentTable.InAppPaymentId {
    val id = SignalDatabase.inAppPayments.insert(
      type = type,
      state = state,
      subscriberId = null,
      endOfPeriod = null,
      inAppPaymentData = data
    )
    if (!notified) {
      val payment = SignalDatabase.inAppPayments.getById(id)!!
      SignalDatabase.inAppPayments.update(payment.copy(notified = false))
    }
    return id
  }

  private fun keepAliveErrorData(): InAppPaymentData = InAppPaymentData(
    error = InAppPaymentData.Error(
      type = InAppPaymentData.Error.Type.REDEMPTION,
      data_ = InAppPaymentKeepAliveJob.KEEP_ALIVE
    )
  )

  // endregion
}
