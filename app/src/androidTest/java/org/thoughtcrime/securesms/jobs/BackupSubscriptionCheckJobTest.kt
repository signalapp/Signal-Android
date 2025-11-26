/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import okio.IOException
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.billing.BillingProduct
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.billing.BillingPurchaseState
import org.signal.core.util.billing.BillingResponseCode
import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.backups.BackupStateObserver
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.storage.IAPSubscriptionId
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription.ChargeFailure
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import org.whispersystems.signalservice.internal.push.WhoAmIResponse
import java.math.BigDecimal
import java.util.Currency
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class BackupSubscriptionCheckJobTest {

  companion object {
    const val IAP_TOKEN = "test_token"
  }

  @get:Rule
  val harness = SignalActivityRule()

  @Before
  fun setUp() {
    mockkObject(RemoteConfig)
    every { RemoteConfig.internalUser } returns true

    coEvery { AppDependencies.billingApi.getApiAvailability() } returns BillingResponseCode.OK

    coEvery { AppDependencies.billingApi.queryPurchases() } returns BillingPurchaseResult.Success(
      purchaseState = BillingPurchaseState.PURCHASED,
      purchaseToken = "test-token",
      isAcknowledged = true,
      isAutoRenewing = true,
      purchaseTime = System.currentTimeMillis()
    )

    coEvery { AppDependencies.billingApi.queryProduct() } returns BillingProduct(price = FiatMoney(BigDecimal.ONE, Currency.getInstance("USD")))

    SignalStore.backup.backupTier = MessageBackupTier.PAID

    mockkObject(RecurringInAppPaymentRepository)
    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription()
    )

    mockkObject(BackupRepository)
    every { BackupRepository.getBackupTier() } answers {
      val tier = SignalStore.backup.backupTier
      if (tier != null) {
        NetworkResult.Success(tier)
      } else {
        NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(404))
      }
    }

    every { BackupRepository.getBackupTierWithoutDowngrade() } answers {
      val tier = SignalStore.backup.backupTier
      if (tier != null) {
        NetworkResult.Success(tier)
      } else {
        NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(404))
      }
    }

    every { BackupRepository.resetInitializedStateAndAuthCredentials() } returns Unit

    mockkObject(BackupStateObserver)
    every { BackupStateObserver.notifyBackupStateChanged() } returns Unit

    mockkObject(SignalNetwork)
    every { AppDependencies.accountApi.whoAmI() } returns NetworkResult.Success(
      WhoAmIResponse(
        number = "+1234567890"
      )
    )

    every { AppDependencies.donationsApi.putSubscription(any()) } returns NetworkResult.Success(Unit)

    insertSubscriber()
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun givenDefaultConfiguration_whenIRun_thenIExpectToQueryPurchases() {
    val job = BackupSubscriptionCheckJob.create()
    job.run()

    coVerify {
      AppDependencies.billingApi.queryPurchases()
    }
  }

  @Test
  fun givenUserIsNotRegistered_whenIRun_thenIExpectSuccessAndEarlyExit() {
    mockkObject(SignalStore.account) {
      every { SignalStore.account.isRegistered } returns false

      val job = BackupSubscriptionCheckJob.create()
      val result = job.run()

      assertEarlyExit(result)
    }
  }

  @Test
  fun givenIsLinkedDevice_whenIRun_thenIExpectSuccessAndEarlyExit() {
    mockkObject(SignalStore.account) {
      every { SignalStore.account.isLinkedDevice } returns true

      val job = BackupSubscriptionCheckJob.create()
      val result = job.run()

      assertEarlyExit(result)
    }
  }

  @Test
  fun givenBillingApiNotAvailable_whenIRun_thenIExpectSuccessAndEarlyExit() {
    coEvery { AppDependencies.billingApi.getApiAvailability() } returns BillingResponseCode.BILLING_UNAVAILABLE

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertEarlyExit(result)
  }

  @Test
  fun givenDeletionStateIsNotNone_whenIRun_thenIExpectSuccessAndEarlyExit() {
    DeletionState.entries.filter { it != DeletionState.NONE }.forEach { deletionState ->
      SignalStore.backup.deletionState = deletionState

      val job = BackupSubscriptionCheckJob.create()
      val result = job.run()

      assertEarlyExit(result)
    }
  }

  @Test
  fun givenBackupsAreNotEnabled_whenIRun_thenIExpectSuccessAndEarlyExit() {
    SignalStore.backup.backupTier = null

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertEarlyExit(result)
  }

  @Test
  fun givenPrePendingRecurringTransaction_whenIRun_thenIExpectSuccessAndEarlyExit() {
    insertPrePendingInAppPayment()

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertEarlyExit(result)
  }

  @Test
  fun givenAPendingPayment_whenIRun_thenIExpectSuccessAndEarlyExit() {
    insertPendingInAppPayment()

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isFalse()
  }

  @Test
  fun givenInactiveSubscription_whenIRun_thenIExpectStateMismatchDetected() {
    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(isActive = false)
    )

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isTrue()
  }

  @Test
  fun givenAnApplicationErrorWhenAccessingTheActiveSubscription_whenIRun_thenIExpectAFailure() {
    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.ApplicationError(
      RuntimeException("Application Error")
    )

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun givenANetworkErrorWhenAccessingTheActiveSubscription_whenIRun_thenIExpectAFailure() {
    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.NetworkError(
      IOException()
    )

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun givenAStatusCodeErrorWhenAccessingTheActiveSubscription_whenIRun_thenIExpectAMismatch() {
    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.StatusCodeError(
      NonSuccessfulResponseCodeException(404)
    )

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isTrue()
  }

  @Test
  fun givenBillingApiReturnsAFailure_whenIRun_thenIExpectSuccessAndEarlyExit() {
    coEvery { AppDependencies.billingApi.queryPurchases() } returns BillingPurchaseResult.BillingUnavailable

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isFalse()
  }

  @Test
  fun givenPastDueSubscription_whenIRun_thenIExpectStateMismatchDetected() {
    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(
        isActive = false,
        billingPeriodEndSeconds = System.currentTimeMillis().milliseconds.inWholeSeconds - 1.days.inWholeSeconds,
        status = "past_due"
      )
    )

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isTrue()
  }

  @Test
  fun givenCancelledSubscription_whenIRun_thenIExpectStateMismatchDetected() {
    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(
        isActive = false,
        status = "canceled",
        cancelled = true
      )
    )

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isTrue()
  }

  @Test
  fun givenFreeBackupTier_whenIRun_thenIExpectSuccessAndEarlyExit() {
    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      ActiveSubscription.EMPTY
    )

    SignalStore.backup.backupTier = MessageBackupTier.FREE

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isFalse()
  }

  @Test
  fun givenActiveSignalSubscriptionWithTokenMismatch_whenIRun_thenIExpectTokenRedemption() {
    mockActivePurchase()
    insertSubscriber("mismatch")

    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(isActive = true)
    )

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isFalse()
    verify {
      RecurringInAppPaymentRepository.ensureSubscriberIdSync(
        eq(InAppPaymentSubscriberRecord.Type.BACKUP),
        eq(true),
        eq(IAPSubscriptionId.GooglePlayBillingPurchaseToken(purchaseToken = "test_token"))
      )
    }
  }

  @Test
  fun givenActiveSubscriptionAndPurchaseWithoutEntitlement_whenIRun_thenIExpectRedemption() {
    mockActivePurchase()

    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(isActive = true)
    )

    // Set backup tier to FREE (no paid entitlement)
    SignalStore.backup.backupTier = MessageBackupTier.FREE

    val job = BackupSubscriptionCheckJob.create()

    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isFalse()
    verify {
      RecurringInAppPaymentRepository.ensureSubscriberIdSync(
        eq(InAppPaymentSubscriberRecord.Type.BACKUP),
        eq(true),
        eq(IAPSubscriptionId.GooglePlayBillingPurchaseToken(purchaseToken = "test_token"))
      )
    }
  }

  @Test
  fun givenValidActiveState_whenIRun_thenIExpectSuccessAndNoMismatch() {
    mockActivePurchase()

    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(isActive = true)
    )

    SignalStore.backup.backupTier = MessageBackupTier.PAID

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isFalse()
  }

  @Test
  fun givenValidInactiveState_whenIRun_thenIExpectSuccessAndNoMismatch() {
    mockInactivePurchase()

    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(isActive = false)
    )

    // Set up valid inactive state: no paid tier + no active subscription + no active purchase
    SignalStore.backup.backupTier = MessageBackupTier.FREE

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isFalse()
  }

  @Test
  fun givenGooglePlayBillingCanceledWithoutActiveSignalSubscription_whenIRun_thenIExpectValidCancelState() {
    mockCanceledPurchase()

    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(isActive = false)
    )

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isFalse()
  }

  @Test
  fun givenGooglePlayBillingCanceledWithFailedSignalSubscription_whenIRun_thenIExpectValidCancelState() {
    mockCanceledPurchase()

    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(isActive = true, status = "past_due", chargeFailure = ChargeFailure("test", "", "", "", ""))
    )

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isFalse()
  }

  @Test
  fun givenInvalidStateConfiguration_whenIRun_thenIExpectStateMismatchDetected() {
    mockActivePurchase()

    // Create invalid state: active purchase but no active subscription, with paid tier
    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(isActive = false)
    )

    SignalStore.backup.backupTier = MessageBackupTier.PAID

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isTrue()
  }

  @Test
  fun givenActiveSubscriptionWithMismatchedZkCredentials_whenIRun_thenIExpectCredentialRefresh() {
    mockActivePurchase()

    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(isActive = true)
    )

    // Set up mismatched state: local tier is PAID but ZK tier is FREE
    SignalStore.backup.backupTier = MessageBackupTier.PAID
    every { BackupRepository.getBackupTierWithoutDowngrade() } returns NetworkResult.Success(MessageBackupTier.FREE)
    every { BackupRepository.resetInitializedStateAndAuthCredentials() } returns Unit

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    verify { BackupRepository.resetInitializedStateAndAuthCredentials() }
    verify { BackupRepository.getBackupTier() }
  }

  @Test
  fun givenActiveSubscriptionWithSyncedZkCredentials_whenIRun_thenIExpectNoCredentialRefresh() {
    mockActivePurchase()

    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(isActive = true)
    )

    // Set up synced state: both local and ZK tiers are PAID
    SignalStore.backup.backupTier = MessageBackupTier.PAID
    every { BackupRepository.getBackupTierWithoutDowngrade() } returns NetworkResult.Success(MessageBackupTier.PAID)

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    verify(exactly = 0) { BackupRepository.resetInitializedStateAndAuthCredentials() }
  }

  @Test
  fun givenActiveSubscriptionWithZkCredentialFailure_whenIRun_thenIExpectCredentialRefresh() {
    mockActivePurchase()

    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(isActive = true)
    )

    SignalStore.backup.backupTier = MessageBackupTier.PAID
    // ZK credential fetch fails, should trigger refresh
    every { BackupRepository.getBackupTierWithoutDowngrade() } returns NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(500))
    every { BackupRepository.resetInitializedStateAndAuthCredentials() } returns Unit

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    verify { BackupRepository.resetInitializedStateAndAuthCredentials() }
  }

  @Test
  fun givenSubscriptionWillCancelAtPeriodEnd_whenIRun_thenIExpectValidCancelState() {
    mockCanceledPurchase()

    // Create subscription that will cancel at period end
    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(isActive = true, cancelled = true) // cancelled = true means willCancelAtPeriodEnd
    )

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isFalse()
  }

  @Test
  fun givenActiveSubscriptionNotWillCancelAtPeriodEnd_whenIRun_thenIExpectZkSynchronization() {
    mockActivePurchase()

    // Create active subscription that won't cancel at period end
    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(isActive = true, cancelled = false)
    )

    SignalStore.backup.backupTier = MessageBackupTier.PAID

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    // Should call ZK synchronization since subscription is active and not canceling
    verify { BackupRepository.getBackupTierWithoutDowngrade() }
  }

  @Test
  fun givenSubscriptionWillCancelWithValidEntitlement_whenIRun_thenIExpectBackupStateNotification() {
    mockCanceledPurchase()

    every { AppDependencies.accountApi.whoAmI() } returns NetworkResult.Success(
      WhoAmIResponse(
        number = "+1234567890",
        entitlements = WhoAmIResponse.Entitlements(
          backup = WhoAmIResponse.BackupEntitlement(
            backupLevel = 201,
            expirationSeconds = System.currentTimeMillis() / 1000 + 3600 // 1 hour from now
          )
        )
      )
    )

    every { RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP) } returns NetworkResult.Success(
      createActiveSubscription(
        isActive = true,
        cancelled = true,
        chargeFailure = ChargeFailure("test", "", "", "", "")
      )
    )

    val job = BackupSubscriptionCheckJob.create()
    val result = job.run()

    assertThat(result.isSuccess).isTrue()
    verify { BackupStateObserver.notifyBackupStateChanged() }
  }

  private fun createActiveSubscription(
    isActive: Boolean = true,
    billingPeriodEndSeconds: Long = 2147472000,
    status: String = "active",
    cancelled: Boolean = false,
    chargeFailure: ChargeFailure? = null
  ): ActiveSubscription {
    return ActiveSubscription(
      ActiveSubscription.Subscription(
        SubscriptionsConfiguration.BACKUPS_LEVEL,
        "USD",
        BigDecimal(42),
        billingPeriodEndSeconds,
        isActive,
        2147472000,
        cancelled,
        status,
        "USA",
        "credit-card",
        false
      ),
      chargeFailure
    )
  }

  private fun insertSubscriber(token: String = IAP_TOKEN) {
    SignalDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        type = InAppPaymentSubscriberRecord.Type.BACKUP,
        iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken(token),
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
        currency = null,
        subscriberId = SubscriberId.generate()
      )
    )
  }

  private fun insertPrePendingInAppPayment() {
    SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_BACKUP,
      state = InAppPaymentTable.State.TRANSACTING,
      subscriberId = null,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData()
    )
  }

  private fun insertPendingInAppPayment() {
    SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.RECURRING_BACKUP,
      state = InAppPaymentTable.State.PENDING,
      subscriberId = null,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData()
    )
  }

  private fun assertEarlyExit(result: Job.Result) {
    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.subscriptionStateMismatchDetected).isFalse()
    coVerify(atLeast = 0, atMost = 0) {
      AppDependencies.billingApi.queryPurchases()
    }
  }

  private fun mockActivePurchase() {
    coEvery { AppDependencies.billingApi.queryPurchases() } returns BillingPurchaseResult.Success(
      purchaseState = BillingPurchaseState.PURCHASED,
      purchaseToken = IAP_TOKEN,
      isAcknowledged = true,
      purchaseTime = System.currentTimeMillis(),
      isAutoRenewing = true
    )
  }

  private fun mockInactivePurchase() {
    coEvery { AppDependencies.billingApi.queryPurchases() } returns BillingPurchaseResult.None
  }

  private fun mockCanceledPurchase() {
    coEvery { AppDependencies.billingApi.queryPurchases() } returns BillingPurchaseResult.Success(
      purchaseState = BillingPurchaseState.PURCHASED,
      purchaseToken = IAP_TOKEN,
      isAcknowledged = true,
      purchaseTime = System.currentTimeMillis(),
      isAutoRenewing = false // Not auto-renewing means canceled
    )
  }
}
