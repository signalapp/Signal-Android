package org.thoughtcrime.securesms.migrations

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.billing.BillingPurchaseState
import org.signal.core.util.billing.BillingResponseCode
import org.signal.core.util.deleteAll
import org.thoughtcrime.securesms.database.InAppPaymentSubscriberTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.whispersystems.signalservice.api.storage.IAPSubscriptionId
import org.whispersystems.signalservice.api.subscriptions.SubscriberId

@RunWith(AndroidJUnit4::class)
class GooglePlayBillingPurchaseTokenMigrationJobTest {
  @get:Rule
  val harness = SignalActivityRule()

  @Before
  fun setUp() {
    SignalDatabase.inAppPaymentSubscribers.writableDatabase.deleteAll(InAppPaymentSubscriberTable.TABLE_NAME)
  }

  @Test
  fun givenNoSubscribers_whenIRunJob_thenIExpectNoBillingAccess() {
    val job = GooglePlayBillingPurchaseTokenMigrationJob()

    job.run()

    verify { AppDependencies.billingApi wasNot Called }
  }

  @Test
  fun givenSubscriberWithAppleData_whenIRunJob_thenIExpectNoBillingAccess() {
    SignalDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        subscriberId = SubscriberId.generate(),
        currency = null,
        type = InAppPaymentSubscriberRecord.Type.BACKUP,
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
        iapSubscriptionId = IAPSubscriptionId.AppleIAPOriginalTransactionId(1000L)
      )
    )

    val job = GooglePlayBillingPurchaseTokenMigrationJob()

    job.run()

    verify { AppDependencies.billingApi wasNot Called }
  }

  @Test
  fun givenSubscriberWithGoogleToken_whenIRunJob_thenIExpectNoBillingAccess() {
    SignalDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        subscriberId = SubscriberId.generate(),
        currency = null,
        type = InAppPaymentSubscriberRecord.Type.BACKUP,
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
        iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken("testToken")
      )
    )

    val job = GooglePlayBillingPurchaseTokenMigrationJob()

    job.run()

    verify { AppDependencies.billingApi wasNot Called }
  }

  @Test
  fun givenSubscriberWithPlaceholderAndNoBillingAccess_whenIRunJob_thenIExpectNoUpdate() {
    SignalDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        subscriberId = SubscriberId.generate(),
        currency = null,
        type = InAppPaymentSubscriberRecord.Type.BACKUP,
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
        iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken("-")
      )
    )

    coEvery { AppDependencies.billingApi.getApiAvailability() } returns BillingResponseCode.BILLING_UNAVAILABLE

    val job = GooglePlayBillingPurchaseTokenMigrationJob()

    job.run()

    val sub = SignalDatabase.inAppPaymentSubscribers.getBackupsSubscriber()

    assertThat(sub?.iapSubscriptionId?.purchaseToken).isEqualTo("-")
  }

  @Test
  fun givenSubscriberWithPlaceholderAndNoPurchase_whenIRunJob_thenIExpectNoUpdate() {
    SignalDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        subscriberId = SubscriberId.generate(),
        currency = null,
        type = InAppPaymentSubscriberRecord.Type.BACKUP,
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
        iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken("-")
      )
    )

    coEvery { AppDependencies.billingApi.getApiAvailability() } returns BillingResponseCode.OK
    coEvery { AppDependencies.billingApi.queryPurchases() } returns BillingPurchaseResult.None

    val job = GooglePlayBillingPurchaseTokenMigrationJob()

    job.run()

    val sub = SignalDatabase.inAppPaymentSubscribers.getBackupsSubscriber()

    assertThat(sub?.iapSubscriptionId?.purchaseToken).isEqualTo("-")
  }

  @Test
  fun givenSubscriberWithPurchase_whenIRunJob_thenIExpectUpdate() {
    SignalDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        subscriberId = SubscriberId.generate(),
        currency = null,
        type = InAppPaymentSubscriberRecord.Type.BACKUP,
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
        iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken("-")
      )
    )

    coEvery { AppDependencies.billingApi.getApiAvailability() } returns BillingResponseCode.OK
    coEvery { AppDependencies.billingApi.queryPurchases() } returns BillingPurchaseResult.Success(
      purchaseState = BillingPurchaseState.PURCHASED,
      purchaseToken = "purchaseToken",
      isAcknowledged = true,
      purchaseTime = System.currentTimeMillis(),
      isAutoRenewing = true
    )

    val job = GooglePlayBillingPurchaseTokenMigrationJob()

    job.run()

    val sub = SignalDatabase.inAppPaymentSubscribers.getBackupsSubscriber()

    assertThat(sub?.iapSubscriptionId?.purchaseToken).isEqualTo("purchaseToken")
  }
}
