package org.thoughtcrime.securesms.migrations

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.count
import org.signal.core.util.readToSingleInt
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.database.InAppPaymentSubscriberTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testing.assertIs
import org.thoughtcrime.securesms.testing.assertIsNotNull
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.util.Currency

@RunWith(AndroidJUnit4::class)
class SubscriberIdMigrationJobTest {

  private val testSubject = SubscriberIdMigrationJob()

  @Test
  fun givenNoSubscriber_whenIRunSubscriberIdMigrationJob_thenIExpectNoDatabaseEntries() {
    testSubject.run()

    val actual = SignalDatabase.inAppPaymentSubscribers.readableDatabase.count()
      .from(InAppPaymentSubscriberTable.TABLE_NAME)
      .run()
      .readToSingleInt()

    actual assertIs 0
  }

  @Test
  fun givenUSDSubscriber_whenIRunSubscriberIdMigrationJob_thenIExpectASingleEntry() {
    val subscriberId = SubscriberId.generate()
    SignalStore.inAppPayments.setSubscriberCurrency(Currency.getInstance("USD"), InAppPaymentSubscriberRecord.Type.DONATION)
    SignalStore.inAppPayments.setSubscriber("USD", subscriberId)
    SignalStore.inAppPayments.setSubscriptionPaymentSourceType(PaymentSourceType.PayPal)
    SignalStore.inAppPayments.shouldCancelSubscriptionBeforeNextSubscribeAttempt = true

    testSubject.run()

    val actual = SignalDatabase.inAppPaymentSubscribers.getByCurrencyCode("USD", InAppPaymentSubscriberRecord.Type.DONATION)

    actual.assertIsNotNull()
    actual!!.subscriberId.bytes assertIs subscriberId.bytes
    actual.paymentMethodType assertIs InAppPaymentData.PaymentMethodType.PAYPAL
    actual.requiresCancel assertIs true
    actual.currency assertIs Currency.getInstance("USD")
    actual.type assertIs InAppPaymentSubscriberRecord.Type.DONATION
  }
}
