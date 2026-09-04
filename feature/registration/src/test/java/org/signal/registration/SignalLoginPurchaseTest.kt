/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.app.Activity
import android.content.Context
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.signal.core.util.billing.BillingPurchaseState
import org.signal.core.util.billing.OneTimeProductResult
import org.signal.core.util.billing.OneTimePurchaseResult
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.RegistrationApiV2.CreateLoginReceiptCredentialError
import org.signal.network.api.RegistrationApiV2.CreateLoginReceiptCredentialResult
import org.signal.network.api.RegistrationApiV2.GetLoginConfigurationError
import org.signal.network.api.RegistrationApiV2.RegisterAccountError
import org.signal.registration.fakes.FakeNetworkController
import org.signal.registration.fakes.FakeOneTimePurchaseApi
import org.signal.registration.fakes.FakeStorageController
import org.signal.registration.fakes.SystemOutLogger
import kotlin.time.Duration.Companion.seconds

/**
 * Covers buying a Signal Login and redeeming it into an account that has no phone number.
 *
 * The fake network controller issues real zkgroup receipt credentials from generated server params, so the credential
 * validation these tests exercise is the real thing.
 */
@RunWith(RobolectricTestRunner::class)
class SignalLoginPurchaseTest {

  private lateinit var networkController: FakeNetworkController
  private lateinit var storageController: FakeStorageController
  private lateinit var purchaseApi: FakeOneTimePurchaseApi
  private lateinit var repository: RegistrationRepository

  private val activity = mockk<Activity>(relaxed = true)

  @Before
  fun setup() {
    Log.initialize(SystemOutLogger())
    networkController = FakeNetworkController()
    storageController = FakeStorageController()
    purchaseApi = FakeOneTimePurchaseApi()
    repository = RegistrationRepository(
      context = mockk<Context>(relaxed = true),
      networkController = networkController,
      storageController = storageController,
      isLinkAndSyncAvailable = false,
      isPhoneNumberlessRegistrationAvailable = true,
      signalLoginPurchaseApi = purchaseApi
    )
  }

  /**
   * Stands in for the UI layer: start the purchase, launch the sheet if the repository asks for one, then hand the
   * outcome back to be redeemed.
   */
  private suspend fun purchaseAndRegister(): SignalLoginPurchaseResult {
    return when (val step = repository.startOrCompleteSignalLoginPurchase()) {
      is SignalLoginPurchaseStep.Finished -> step.result
      is SignalLoginPurchaseStep.LaunchRequired -> repository.completeSignalLoginPurchase(step.launcher.launch(activity))
    }
  }

  // ==================== configuration ====================

  @Test
  fun `getSignalLoginPrice asks Google Play for the product the service named`() = runTest {
    val price = repository.getSignalLoginPrice()

    assertThat(price).isEqualTo(SignalLoginPriceResult.Available("$1.99"))
    assertThat(purchaseApi.requestedProducts.map { it.productId }).containsExactly(FakeNetworkController.LOGIN_PLAY_PRODUCT_ID)
    assertThat(purchaseApi.requestedProducts.single().purchaseOptionId).isEqualTo(RegistrationRepository.SIGNAL_LOGIN_PURCHASE_OPTION_ID)
  }

  @Test
  fun `getSignalLoginPrice reports a transient error when the service will not say what to sell`() = runTest {
    networkController.onGetLoginConfiguration = { RequestResult.NonSuccess(GetLoginConfigurationError.RateLimited(30.seconds)) }

    assertThat(repository.getSignalLoginPrice()).isEqualTo(SignalLoginPriceResult.TransientError)
    assertThat(purchaseApi.requestedProducts).isEmpty()
  }

  @Test
  fun `getSignalLoginPrice reports a transient billing failure as retryable`() = runTest {
    purchaseApi.formattedPrice = null
    purchaseApi.productResultWhenUnpriced = OneTimeProductResult.TransientError

    assertThat(repository.getSignalLoginPrice()).isEqualTo(SignalLoginPriceResult.TransientError)
  }

  @Test
  fun `getSignalLoginPrice reports unavailable when Google Play will not price the product`() = runTest {
    purchaseApi.formattedPrice = null

    assertThat(repository.getSignalLoginPrice()).isEqualTo(SignalLoginPriceResult.Unavailable)
  }

  @Test
  fun `concurrent callers share a single login configuration fetch`() = runTest {
    var fetches = 0
    val gate = CompletableDeferred<Unit>()
    val default = networkController.onGetLoginConfiguration
    networkController.onGetLoginConfiguration = {
      fetches++
      gate.await()
      default()
    }

    val first = async { repository.getSignalLoginPrice() }
    val second = async { repository.hasUnredeemedSignalLoginPurchase() }

    gate.complete(Unit)

    assertThat(first.await()).isEqualTo(SignalLoginPriceResult.Available("$1.99"))
    assertThat(second.await()).isFalse()
    assertThat(fetches).isEqualTo(1)
  }

  @Test
  fun `a failed login configuration fetch is retried rather than cached`() = runTest {
    var fetches = 0
    val default = networkController.onGetLoginConfiguration
    networkController.onGetLoginConfiguration = {
      fetches++
      if (fetches == 1) RequestResult.NonSuccess(GetLoginConfigurationError.RateLimited(30.seconds)) else default()
    }

    assertThat(repository.getSignalLoginPrice()).isEqualTo(SignalLoginPriceResult.TransientError)
    assertThat(repository.getSignalLoginPrice()).isEqualTo(SignalLoginPriceResult.Available("$1.99"))
    assertThat(fetches).isEqualTo(2)
  }

  @Test
  fun `the login configuration is only fetched once`() = runTest {
    var fetches = 0
    val default = networkController.onGetLoginConfiguration
    networkController.onGetLoginConfiguration = {
      fetches++
      default()
    }

    repository.getSignalLoginPrice()
    repository.getSignalLoginPrice()
    repository.hasUnredeemedSignalLoginPurchase()

    assertThat(fetches).isEqualTo(1)
  }

  // ==================== the happy path ====================

  @Test
  fun `startOrCompleteSignalLoginPurchase registers an account and consumes the purchase`() = runTest {
    val result = purchaseAndRegister()

    assertThat(result).isInstanceOf(SignalLoginPurchaseResult.Registered::class)
    assertThat(networkController.lastLoginPurchaseIdentifier).isEqualTo(FakeOneTimePurchaseApi.PURCHASE_TOKEN)
    assertThat(purchaseApi.consumedTokens).containsExactly(FakeOneTimePurchaseApi.PURCHASE_TOKEN)
  }

  @Test
  fun `a registered account has no phone number`() = runTest {
    purchaseAndRegister()

    val request = networkController.lastRegisterAccountRequest
    assertThat(request).isNotNull()
    assertThat(request!!.e164).isNull()
    assertThat(request.sessionId).isNull()
    assertThat(request.recoveryPassword).isNull()
  }

  @Test
  fun `the persisted purchase is cleared once it has been redeemed`() = runTest {
    purchaseAndRegister()

    assertThat(storageController.readInProgressRegistrationData().signalLoginPurchase).isNull()
  }

  // ==================== recovering a purchase ====================

  @Test
  fun `an unconsumed purchase is reused instead of charging again`() = runTest {
    purchaseApi.ownedPurchase = FakeOneTimePurchaseApi.purchase()
    purchaseApi.onLaunchPurchaseFlow = { error("Should not have charged the user again.") }

    val result = purchaseAndRegister()

    assertThat(result).isInstanceOf(SignalLoginPurchaseResult.Registered::class)
    assertThat(purchaseApi.consumedTokens).containsExactly(FakeOneTimePurchaseApi.PURCHASE_TOKEN)
  }

  @Test
  fun `a retry reuses the receipt credential request the purchase started with`() = runTest {
    networkController.onCreateLoginPurchaseReceiptCredential = { RequestResult.RetryableNetworkError(mockk(relaxed = true)) }
    purchaseApi.ownedPurchase = FakeOneTimePurchaseApi.purchase()

    val first = purchaseAndRegister()
    assertThat(first).isInstanceOf(SignalLoginPurchaseResult.NetworkError::class)

    networkController.onCreateLoginPurchaseReceiptCredential = { request -> RequestResult.Success(networkController.issueLoginReceiptCredential(request)) }
    val second = purchaseAndRegister()

    assertThat(second).isInstanceOf(SignalLoginPurchaseResult.Registered::class)
    assertThat(networkController.loginReceiptCredentialRequests).hasSize(2)
    assertThat(networkController.loginReceiptCredentialRequests[0].serialize().toList())
      .isEqualTo(networkController.loginReceiptCredentialRequests[1].serialize().toList())
  }

  @Test
  fun `a failed redemption leaves the purchase unconsumed so it can be retried`() = runTest {
    networkController.onCreateLoginPurchaseReceiptCredential = { RequestResult.NonSuccess(CreateLoginReceiptCredentialError.PurchaseNotFound) }
    purchaseApi.ownedPurchase = FakeOneTimePurchaseApi.purchase()

    val result = purchaseAndRegister()

    assertThat(result).isInstanceOf(SignalLoginPurchaseResult.RedemptionFailed::class)
    assertThat(purchaseApi.consumedTokens).isEmpty()
    assertThat(repository.hasUnredeemedSignalLoginPurchase()).isTrue()
  }

  @Test
  fun `a failed registration leaves the purchase unconsumed so it can be retried`() = runTest {
    networkController.onRegisterAccount = { RequestResult.NonSuccess(RegisterAccountError.InvalidReceiptCredentialPresentation("nope")) }

    val result = purchaseAndRegister()

    assertThat(result).isInstanceOf(SignalLoginPurchaseResult.RegistrationFailed::class)
    assertThat(purchaseApi.consumedTokens).isEmpty()
  }

  // ==================== pending payment ====================

  @Test
  fun `a purchase pending with Google Play persists its request context and reports as pending`() = runTest {
    purchaseApi.onLaunchPurchaseFlow = { OneTimePurchaseResult.Success(FakeOneTimePurchaseApi.purchase(state = BillingPurchaseState.PENDING)) }

    val result = purchaseAndRegister()

    assertThat(result).isInstanceOf(SignalLoginPurchaseResult.PurchasePending::class)
    assertThat(networkController.loginReceiptCredentialRequests).isEmpty()

    val persisted = storageController.readInProgressRegistrationData().signalLoginPurchase
    assertThat(persisted).isNotNull()
    assertThat(persisted!!.purchaseToken).isEqualTo(FakeOneTimePurchaseApi.PURCHASE_TOKEN)
  }

  @Test
  fun `a purchase pending with the service reports as pending`() = runTest {
    networkController.onCreateLoginPurchaseReceiptCredential = { RequestResult.Success(CreateLoginReceiptCredentialResult.PurchasePending) }

    val result = purchaseAndRegister()

    assertThat(result).isInstanceOf(SignalLoginPurchaseResult.PurchasePending::class)
    assertThat(purchaseApi.consumedTokens).isEmpty()
  }

  // ==================== failing before payment ====================

  @Test
  fun `nothing is charged when the service will not say what to sell`() = runTest {
    networkController.onGetLoginConfiguration = { RequestResult.NonSuccess(GetLoginConfigurationError.RateLimited(30.seconds)) }

    val result = purchaseAndRegister()

    assertThat(result).isInstanceOf(SignalLoginPurchaseResult.NetworkError::class)
    assertThat(purchaseApi.launchCount).isEqualTo(0)
  }

  @Test
  fun `a cancelled purchase reports as cancelled`() = runTest {
    purchaseApi.onLaunchPurchaseFlow = { OneTimePurchaseResult.UserCancelled }

    val result = purchaseAndRegister()

    assertThat(result).isInstanceOf(SignalLoginPurchaseResult.Cancelled::class)
    assertThat(networkController.loginReceiptCredentialRequests).isEmpty()
  }

  @Test
  fun `an unavailable purchase reports as unavailable`() = runTest {
    purchaseApi.onLaunchPurchaseFlow = { OneTimePurchaseResult.Unavailable }

    assertThat(purchaseAndRegister()).isInstanceOf(SignalLoginPurchaseResult.PurchaseUnavailable::class)
  }

  @Test
  fun `hasUnredeemedSignalLoginPurchase is false when Google Play has nothing owned`() = runTest {
    assertThat(repository.hasUnredeemedSignalLoginPurchase()).isFalse()
  }

  // ==================== teardown ====================

  @Test
  fun `closing the repository releases the billing connection`() = runTest {
    repository.close()

    assertThat(purchaseApi.closeCount).isEqualTo(1)
  }

  // ==================== a consume Google Play refuses ====================

  @Test
  fun `a purchase Google Play will not consume keeps its persisted request context`() = runTest {
    purchaseApi.consumeSucceeds = false

    val result = purchaseAndRegister()

    assertThat(result).isInstanceOf(SignalLoginPurchaseResult.Registered::class)
    assertThat(purchaseApi.consumedTokens).containsExactly(FakeOneTimePurchaseApi.PURCHASE_TOKEN)

    // Dropping the context here would strand the still-unconsumed purchase on a permanent AlreadyRedeemed.
    val persisted = storageController.readInProgressRegistrationData().signalLoginPurchase
    assertThat(persisted).isNotNull()
    assertThat(persisted!!.purchaseToken).isEqualTo(FakeOneTimePurchaseApi.PURCHASE_TOKEN)
  }

  @Test
  fun `a retry after a refused consume reuses the original receipt credential request`() = runTest {
    purchaseApi.consumeSucceeds = false

    purchaseAndRegister()
    val second = purchaseAndRegister()

    assertThat(second).isInstanceOf(SignalLoginPurchaseResult.Registered::class)
    assertThat(networkController.loginReceiptCredentialRequests).hasSize(2)
    assertThat(networkController.loginReceiptCredentialRequests[0].serialize().toList())
      .isEqualTo(networkController.loginReceiptCredentialRequests[1].serialize().toList())
  }
}
