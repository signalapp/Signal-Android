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

  // endregion

  // region helpers

  private fun insertDonation(notified: Boolean): InAppPaymentTable.InAppPaymentId = insertPayment(type = InAppPaymentType.ONE_TIME_DONATION, notified = notified)

  private fun insertBackup(notified: Boolean): InAppPaymentTable.InAppPaymentId = insertPayment(type = InAppPaymentType.RECURRING_BACKUP, notified = notified)

  private fun insertPayment(type: InAppPaymentType, notified: Boolean): InAppPaymentTable.InAppPaymentId {
    val id = SignalDatabase.inAppPayments.insert(
      type = type,
      state = InAppPaymentTable.State.CREATED,
      subscriberId = null,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData()
    )
    if (!notified) {
      val payment = SignalDatabase.inAppPayments.getById(id)!!
      SignalDatabase.inAppPayments.update(payment.copy(notified = false))
    }
    return id
  }

  // endregion
}
