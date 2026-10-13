/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule
import org.whispersystems.signalservice.api.storage.IAPSubscriptionId
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import java.math.BigDecimal

/**
 * Verifies how we identify a backup subscription that is billed through a store other than Google Play, which is what
 * distinguishes a user who transferred from another platform from a user whose Google Play purchase has genuinely gone
 * away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class InAppPaymentsRepositoryBackupStoreTest {

  @get:Rule
  val signalStore = MockSignalStoreRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

  @Test
  fun givenAnAppleSubscriber_whenICheckBillingStore_thenIExpectOtherStore() {
    insertBackupSubscriber(IAPSubscriptionId.AppleIAPOriginalTransactionId(1000L))

    assertThat(InAppPaymentsRepository.isBackupBilledThroughOtherStore()).isTrue()
  }

  @Test
  fun givenAGooglePlaySubscriber_whenICheckBillingStore_thenIExpectNotOtherStore() {
    insertBackupSubscriber(IAPSubscriptionId.GooglePlayBillingPurchaseToken("test_token"))

    assertThat(InAppPaymentsRepository.isBackupBilledThroughOtherStore()).isFalse()
  }

  @Test
  fun givenNoSubscriber_whenICheckBillingStore_thenIExpectNotOtherStore() {
    assertThat(InAppPaymentsRepository.isBackupBilledThroughOtherStore()).isFalse()
  }

  @Test
  fun givenAGooglePlaySubscriberAndAnAppleBilledSubscription_whenICheckBillingStore_thenIExpectOtherStore() {
    insertBackupSubscriber(IAPSubscriptionId.GooglePlayBillingPurchaseToken("test_token"))

    val result = InAppPaymentsRepository.isBackupBilledThroughOtherStore(
      createSubscription(ActiveSubscription.PaymentMethod.APPLE_APP_STORE)
    )

    assertThat(result).isTrue()
  }

  @Test
  fun givenAnAppleSubscriberAndAGoogleBilledSubscription_whenICheckBillingStore_thenIExpectOtherStore() {
    insertBackupSubscriber(IAPSubscriptionId.AppleIAPOriginalTransactionId(1000L))

    val result = InAppPaymentsRepository.isBackupBilledThroughOtherStore(
      createSubscription(ActiveSubscription.PaymentMethod.GOOGLE_PLAY_BILLING)
    )

    assertThat(result).isTrue()
  }

  @Test
  fun givenAGooglePlaySubscriberAndAGoogleBilledSubscription_whenICheckBillingStore_thenIExpectNotOtherStore() {
    insertBackupSubscriber(IAPSubscriptionId.GooglePlayBillingPurchaseToken("test_token"))

    val result = InAppPaymentsRepository.isBackupBilledThroughOtherStore(
      createSubscription(ActiveSubscription.PaymentMethod.GOOGLE_PLAY_BILLING)
    )

    assertThat(result).isFalse()
  }

  private fun insertBackupSubscriber(iapSubscriptionId: IAPSubscriptionId) {
    SignalDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        subscriberId = SubscriberId.generate(),
        currency = null,
        type = InAppPaymentSubscriberRecord.Type.BACKUP,
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.UNKNOWN,
        iapSubscriptionId = iapSubscriptionId
      )
    )
  }

  private fun createSubscription(paymentMethod: ActiveSubscription.PaymentMethod): ActiveSubscription.Subscription {
    val subscription = ActiveSubscription.Subscription(
      SubscriptionsConfiguration.BACKUPS_LEVEL,
      "USD",
      BigDecimal(299),
      2147472000L,
      true,
      2147472000L,
      false,
      "active",
      ActiveSubscription.Processor.GOOGLE_PLAY_BILLING.code,
      paymentMethod.name,
      false
    )

    assertThat(subscription.paymentMethod).isEqualTo(paymentMethod)

    return subscription
  }
}
