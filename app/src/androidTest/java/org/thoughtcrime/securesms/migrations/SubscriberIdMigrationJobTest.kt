package org.thoughtcrime.securesms.migrations

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
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

    assertThat(actual).isEqualTo(0)
  }

  @Test
  fun givenUSDSubscriber_whenIRunSubscriberIdMigrationJob_thenIExpectASingleEntry() {
    val subscriberId = SubscriberId.generate()
    SignalStore.inAppPayments.setRecurringDonationCurrency(Currency.getInstance("USD"))
    SignalStore.inAppPayments.setSubscriber("USD", subscriberId)
    SignalStore.inAppPayments.setSubscriptionPaymentSourceType(PaymentSourceType.PayPal)
    SignalStore.inAppPayments.shouldCancelSubscriptionBeforeNextSubscribeAttempt = true

    testSubject.run()

    val actual = SignalDatabase.inAppPaymentSubscribers.getByCurrencyCode("USD")

    assertThat(actual)
      .isNotNull()
      .given {
        assertThat(it.subscriberId.bytes).isEqualTo(subscriberId.bytes)
        assertThat(it.paymentMethodType).isEqualTo(InAppPaymentData.PaymentMethodType.PAYPAL)
        assertThat(it.requiresCancel).isTrue()
        assertThat(it.currency).isEqualTo(Currency.getInstance("USD"))
        assertThat(it.type).isEqualTo(InAppPaymentSubscriberRecord.Type.DONATION)
      }
  }
}
