package org.thoughtcrime.securesms.jobs

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import okhttp3.mockwebserver.MockResponse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.billing.BillingPurchaseResult
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.InstrumentationApplicationDependencyProvider
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testing.Get
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.assertIs
import org.thoughtcrime.securesms.testing.assertIsNull
import org.thoughtcrime.securesms.testing.success
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.math.BigDecimal
import java.util.Currency
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class BackupSubscriptionCheckJobTest {
  @get:Rule
  val harness = SignalActivityRule()

  private val testSubject = BackupSubscriptionCheckJob.create()

  @Before
  fun setUp() {
    mockkStatic(AppDependencies::class)
    mockkStatic(RemoteConfig::class)

    every { RemoteConfig.messageBackups } returns true
    every { AppDependencies.billingApi } returns mockk()
    every { AppDependencies.billingApi.isApiAvailable() } returns true
    coEvery { AppDependencies.billingApi.queryPurchases() } returns BillingPurchaseResult.None

    val billingApi = AppDependencies.billingApi

    every { billingApi.isApiAvailable() } returns true
  }

  @Test
  fun givenMessageBackupsAreDisabled_whenICheck_thenIExpectSuccess() {
    every { RemoteConfig.messageBackups } returns false

    val result = testSubject.run()

    result.isSuccess.assertIs(true)
  }

  @Test
  fun givenBillingApiIsUnavailable_whenICheck_thenIExpectSuccess() {
    every { AppDependencies.billingApi.isApiAvailable() } returns false

    val result = testSubject.run()

    result.isSuccess.assertIs(true)
  }

  @Test
  fun givenAGooglePlaySubscriptionAndNoSubscriberId_whenICheck_thenIExpectToTurnOffBackups() {
    coEvery { AppDependencies.billingApi.queryPurchases() } returns BillingPurchaseResult.Success(
      purchaseToken = "",
      isAcknowledged = true,
      purchaseTime = System.currentTimeMillis(),
      isAutoRenewing = true
    )

    SignalStore.backup.backupTier = MessageBackupTier.PAID

    val result = testSubject.run()

    result.isSuccess.assertIs(true)
    SignalStore.backup.backupTier.assertIsNull()
  }

  @Test
  fun givenNoSubscriberIdButPaidTier_whenICheck_thenIExpectToTurnOffBackups() {
    SignalStore.backup.backupTier = MessageBackupTier.PAID

    val result = testSubject.run()

    result.isSuccess.assertIs(true)
    SignalStore.backup.backupTier.assertIsNull()
  }

  @Test
  fun givenActiveSubscription_whenICheck_thenIExpectToTurnOnBackups() {
    initialiseActiveSubscription()
    SignalStore.backup.backupTier = null

    val result = testSubject.run()

    result.isSuccess.assertIs(true)
    SignalStore.backup.backupTier.assertIs(MessageBackupTier.PAID)
  }

  fun givenInactiveSubscription_whenICheck_thenIExpectToTurnOffBackups() {
    initialiseActiveSubscription("canceled")
    SignalStore.backup.backupTier = MessageBackupTier.PAID

    val result = testSubject.run()

    result.isSuccess.assertIs(true)
    SignalStore.backup.backupTier.assertIsNull()
  }

  fun givenInactiveSubscriptionAndNoLocalState_whenICheck_thenIExpectToTurnOffBackups() {
    initialiseActiveSubscription("canceled")
    SignalStore.backup.backupTier = null

    val result = testSubject.run()

    result.isSuccess.assertIs(true)
    SignalStore.backup.backupTier.assertIsNull()
  }

  private fun initialiseActiveSubscription(status: String = "active") {
    val currency = Currency.getInstance("USD")
    val subscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = currency,
      type = InAppPaymentSubscriberRecord.Type.BACKUP,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.CARD
    )

    InAppPaymentsRepository.setSubscriber(subscriber)
    SignalStore.inAppPayments.setSubscriberCurrency(currency, subscriber.type)

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Get("/v1/subscription/${subscriber.subscriberId.serialize()}") {
        MockResponse().success(
          ActiveSubscription(
            ActiveSubscription.Subscription(
              201,
              currency.currencyCode,
              BigDecimal.ONE,
              System.currentTimeMillis().milliseconds.inWholeSeconds + 30.days.inWholeSeconds,
              true,
              System.currentTimeMillis().milliseconds.inWholeSeconds + 30.days.inWholeSeconds,
              false,
              status,
              "STRIPE",
              "GOOGLE_PLAY_BILLING",
              false
            ),
            null
          )
        )
      }
    )
  }
}
