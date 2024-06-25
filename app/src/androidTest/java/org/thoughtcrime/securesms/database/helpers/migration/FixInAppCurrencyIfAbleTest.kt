/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toDecimalValue
import org.thoughtcrime.securesms.database.InAppPaymentSubscriberTable
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.FiatValue
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.testing.SignalDatabaseRule
import org.thoughtcrime.securesms.testing.assertIs
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.math.BigDecimal
import java.util.Currency

@RunWith(AndroidJUnit4::class)
class FixInAppCurrencyIfAbleTest {

  @get:Rule
  val harness = SignalDatabaseRule(deleteAllThreadsOnEachRun = false)

  @Test
  fun givenNoSubscribers_whenIMigrate_thenIDoNothing() {
    migrate()
  }

  @Test
  fun givenASubscriberButNoPayment_whenIMigrate_thenIDoNothing() {
    val subscriber = insertSubscriber("USD")
    clearCurrencyCode(subscriber)
    migrate()

    getCurrencyCode(subscriber) assertIs ""
  }

  @Test
  fun givenASubscriberAndMismatchedPayment_whenIMigrate_thenIDoNothing() {
    val subscriber = insertSubscriber("USD")
    val otherSubscriber = insertSubscriber("EUR")
    insertPayment(otherSubscriber)
    clearCurrencyCode(subscriber)
    migrate()

    getCurrencyCode(subscriber) assertIs ""
  }

  @Test
  fun givenASubscriberAndPaymentWithNoSubscriber_whenIMigrate_thenDoNothing() {
    val subscriber = insertSubscriber("USD")
    insertPayment(null)
    clearCurrencyCode(subscriber)
    migrate()

    getCurrencyCode(subscriber) assertIs ""
  }

  @Test
  fun givenASubscriberAndMatchingPayment_whenIMigrate_thenUpdateCurrencyCode() {
    val subscriber = insertSubscriber("USD")
    insertPayment(subscriber)
    clearCurrencyCode(subscriber)
    migrate()

    getCurrencyCode(subscriber) assertIs "USD"
  }

  @Test
  fun givenASupercededSubscriber_whenIMigrate_thenIDoNothing() {
    val oldSubscriber = insertSubscriber("USD")
    insertPayment(oldSubscriber)
    clearCurrencyCode(oldSubscriber)
    insertSubscriber("USD")
    migrate()
  }

  private fun migrate() {
    V236_FixInAppSubscriberCurrencyIfAble.migrate(
      context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application,
      db = SignalDatabase.rawDatabase,
      oldVersion = 0,
      newVersion = 0
    )
  }

  private fun insertSubscriber(currencyCode: String): InAppPaymentSubscriberRecord {
    val record = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = Currency.getInstance(currencyCode),
      type = InAppPaymentSubscriberRecord.Type.DONATION,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.PAYPAL
    )

    SignalDatabase.inAppPaymentSubscribers.insertOrReplace(record)

    return record
  }

  private fun clearCurrencyCode(inAppPaymentSubscriberRecord: InAppPaymentSubscriberRecord) {
    SignalDatabase.rawDatabase.update(InAppPaymentSubscriberTable.TABLE_NAME)
      .values(InAppPaymentSubscriberTable.CURRENCY_CODE to "")
      .where("${InAppPaymentSubscriberTable.SUBSCRIBER_ID} = ?", inAppPaymentSubscriberRecord.subscriberId.serialize())
      .run()
  }

  private fun getCurrencyCode(inAppPaymentSubscriberRecord: InAppPaymentSubscriberRecord): String {
    return SignalDatabase.rawDatabase.select(InAppPaymentSubscriberTable.CURRENCY_CODE)
      .from(InAppPaymentSubscriberTable.TABLE_NAME)
      .where("${InAppPaymentSubscriberTable.SUBSCRIBER_ID} = ?", inAppPaymentSubscriberRecord.subscriberId.serialize())
      .run()
      .readToSingleObject { it.requireNonNullString(InAppPaymentSubscriberTable.CURRENCY_CODE) }!!
  }

  private fun insertPayment(inAppPaymentSubscriberRecord: InAppPaymentSubscriberRecord?): InAppPaymentTable.InAppPayment {
    val id = SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_DONATION,
      state = InAppPaymentTable.State.END,
      subscriberId = inAppPaymentSubscriberRecord?.subscriberId,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData(
        amount = FiatValue(
          currencyCode = inAppPaymentSubscriberRecord?.currency?.currencyCode ?: "USD",
          amount = BigDecimal.ONE.toDecimalValue()
        ),
        level = 200,
        paymentMethodType = inAppPaymentSubscriberRecord?.paymentMethodType ?: InAppPaymentData.PaymentMethodType.UNKNOWN
      )
    )

    return SignalDatabase.inAppPayments.getById(id)!!
  }
}
