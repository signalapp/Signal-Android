/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.util.billing.BillingPurchaseState
import org.signal.core.util.billing.OneTimePurchase
import org.signal.core.util.billing.OneTimePurchaseResult
import org.signal.core.util.billing.PurchaseLauncher
import org.signal.network.api.RegistrationApiV2.CreateLoginReceiptCredentialError
import org.signal.registration.RegisteredAccountData
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.SignalLoginPriceResult
import org.signal.registration.SignalLoginPurchaseResult
import org.signal.registration.SignalLoginPurchaseStep

@OptIn(ExperimentalCoroutinesApi::class)
class SignalLoginPaymentViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var mockRepository: RegistrationRepository
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var viewModel: SignalLoginPaymentViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
    every { mockRepository.isGooglePlayBillingAvailable } returns true
    parentEventEmitter = {}
    viewModel = SignalLoginPaymentViewModel(
      repository = mockRepository,
      parentEventEmitter = parentEventEmitter
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun TestScope.collectActions(): List<SignalLoginPaymentScreenActions> {
    val actions = mutableListOf<SignalLoginPaymentScreenActions>()
    backgroundScope.launch(testDispatcher) { viewModel.actions.collect { actions.add(it) } }
    return actions
  }

  private fun collectParentEvents(): Pair<List<RegistrationFlowEvent>, (RegistrationFlowEvent) -> Unit> {
    val events = mutableListOf<RegistrationFlowEvent>()
    return events to { event: RegistrationFlowEvent -> events.add(event) }
  }

  private suspend fun applyEvent(state: SignalLoginPaymentState, event: SignalLoginPaymentScreenEvents, emitter: (RegistrationFlowEvent) -> Unit = parentEventEmitter): SignalLoginPaymentState {
    var result = state
    viewModel.applyEvent(state, event, emitter) { result = it }
    return result
  }

  @Test
  fun `LearnMoreClicked emits an action to open the learn more article`() = runTest(testDispatcher) {
    val actions = collectActions()

    viewModel.applyEvent(SignalLoginPaymentState(), SignalLoginPaymentScreenEvents.LearnMoreClicked, parentEventEmitter) {}

    assertThat(actions).containsExactly(SignalLoginPaymentScreenActions.OpenLearnMoreArticle)
  }

  @Test
  fun `Initialize loads the price from the billing library`() = runTest(testDispatcher) {
    coEvery { mockRepository.getSignalLoginPrice() } returns SignalLoginPriceResult.Available("$1.99")
    coEvery { mockRepository.hasUnredeemedSignalLoginPurchase() } returns false

    val state = applyEvent(SignalLoginPaymentState(), SignalLoginPaymentScreenEvents.Initialize)

    assertThat(state.price).isEqualTo(SignalLoginPaymentState.Price.Available("$1.99"))
    assertThat(state.hasUnredeemedPurchase).isFalse()
  }

  @Test
  fun `Initialize marks the purchase unavailable when there is no price`() = runTest(testDispatcher) {
    coEvery { mockRepository.getSignalLoginPrice() } returns SignalLoginPriceResult.Unavailable
    coEvery { mockRepository.hasUnredeemedSignalLoginPurchase() } returns false

    val state = applyEvent(SignalLoginPaymentState(), SignalLoginPaymentScreenEvents.Initialize)

    assertThat(state.price).isEqualTo(SignalLoginPaymentState.Price.Unavailable)
  }

  @Test
  fun `Initialize disables the purchase option and skips the price lookup when Play billing is unavailable`() = runTest(testDispatcher) {
    every { mockRepository.isGooglePlayBillingAvailable } returns false
    coEvery { mockRepository.hasUnredeemedSignalLoginPurchase() } returns false
    coEvery { mockRepository.getSignalLoginPrice() } returns SignalLoginPriceResult.TransientError

    val state = applyEvent(SignalLoginPaymentState(), SignalLoginPaymentScreenEvents.Initialize)

    assertThat(state.isPurchaseSupported).isFalse()
    assertThat(state.isPurchaseOptionEnabled).isFalse()
    assertThat(state.selectedOption).isEqualTo(SignalLoginPaymentState.Option.ExistingLogin)
    assertThat(state.price).isEqualTo(SignalLoginPaymentState.Price.Unavailable)
  }

  @Test
  fun `Initialize keeps the purchase option enabled without Play billing when a purchase is already paid for`() = runTest(testDispatcher) {
    every { mockRepository.isGooglePlayBillingAvailable } returns false
    coEvery { mockRepository.hasUnredeemedSignalLoginPurchase() } returns true

    val state = applyEvent(SignalLoginPaymentState(), SignalLoginPaymentScreenEvents.Initialize)

    assertThat(state.isPurchaseSupported).isFalse()
    assertThat(state.isPurchaseOptionEnabled).isTrue()
    assertThat(state.selectedOption).isEqualTo(SignalLoginPaymentState.Option.Purchase)
  }

  @Test
  fun `OptionSelected ignores the purchase option when it cannot be acted on`() = runTest(testDispatcher) {
    val state = applyEvent(
      SignalLoginPaymentState(isPurchaseSupported = false, selectedOption = SignalLoginPaymentState.Option.ExistingLogin),
      SignalLoginPaymentScreenEvents.OptionSelected(SignalLoginPaymentState.Option.Purchase)
    )

    assertThat(state.selectedOption).isEqualTo(SignalLoginPaymentState.Option.ExistingLogin)
  }

  @Test
  fun `Initialize offers a retry when the price lookup fails transiently`() = runTest(testDispatcher) {
    coEvery { mockRepository.getSignalLoginPrice() } returns SignalLoginPriceResult.TransientError
    coEvery { mockRepository.hasUnredeemedSignalLoginPurchase() } returns false

    val state = applyEvent(SignalLoginPaymentState(), SignalLoginPaymentScreenEvents.Initialize)

    assertThat(state.price).isEqualTo(SignalLoginPaymentState.Price.TransientError)
    assertThat(state.isActionEnabled).isFalse()
  }

  @Test
  fun `PriceRetryClicked re-fetches the price and recovers`() = runTest(testDispatcher) {
    coEvery { mockRepository.getSignalLoginPrice() } returns SignalLoginPriceResult.Available("$1.99")

    val state = applyEvent(
      SignalLoginPaymentState(price = SignalLoginPaymentState.Price.TransientError),
      SignalLoginPaymentScreenEvents.PriceRetryClicked
    )

    assertThat(state.price).isEqualTo(SignalLoginPaymentState.Price.Available("$1.99"))
    assertThat(state.isActionEnabled).isTrue()
  }

  @Test
  fun `a failed price lookup does not block logging in with an existing login`() = runTest(testDispatcher) {
    coEvery { mockRepository.getSignalLoginPrice() } returns SignalLoginPriceResult.TransientError
    coEvery { mockRepository.hasUnredeemedSignalLoginPurchase() } returns false

    var state = applyEvent(SignalLoginPaymentState(), SignalLoginPaymentScreenEvents.Initialize)
    state = applyEvent(state, SignalLoginPaymentScreenEvents.OptionSelected(SignalLoginPaymentState.Option.ExistingLogin))

    assertThat(state.isActionEnabled).isTrue()
  }

  @Test
  fun `Initialize surfaces a purchase that was paid for but never redeemed`() = runTest(testDispatcher) {
    coEvery { mockRepository.getSignalLoginPrice() } returns SignalLoginPriceResult.Available("$1.99")
    coEvery { mockRepository.hasUnredeemedSignalLoginPurchase() } returns true

    val state = applyEvent(SignalLoginPaymentState(), SignalLoginPaymentScreenEvents.Initialize)

    assertThat(state.hasUnredeemedPurchase).isTrue()
  }

  @Test
  fun `ContinueClicked on the existing login option navigates to credential entry`() = runTest(testDispatcher) {
    val (events, emitter) = collectParentEvents()

    applyEvent(
      SignalLoginPaymentState(selectedOption = SignalLoginPaymentState.Option.ExistingLogin),
      SignalLoginPaymentScreenEvents.ContinueClicked,
      emitter
    )

    assertThat(events).containsExactly(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.SignalLoginCredentialEntry(), false))
    coVerify(exactly = 0) { mockRepository.startOrCompleteSignalLoginPurchase() }
  }

  @Test
  fun `ContinueClicked on the purchase option asks the UI layer to launch the sheet`() = runTest(testDispatcher) {
    val launcher = PurchaseLauncher { OneTimePurchaseResult.UserCancelled }
    coEvery { mockRepository.startOrCompleteSignalLoginPurchase() } returns SignalLoginPurchaseStep.LaunchRequired(launcher)
    val actions = collectActions()

    val state = applyEvent(
      SignalLoginPaymentState(price = SignalLoginPaymentState.Price.Available("$1.99")),
      SignalLoginPaymentScreenEvents.ContinueClicked
    )

    assertThat(actions).containsExactly(SignalLoginPaymentScreenActions.LaunchPurchaseFlow(launcher))
    // The spinner stays up while the sheet is open; PurchaseFlowCompleted clears it.
    assertThat(state.showSpinner).isTrue()
  }

  @Test
  fun `ContinueClicked applies a step that finished without needing a launch`() = runTest(testDispatcher) {
    coEvery { mockRepository.startOrCompleteSignalLoginPurchase() } returns
      SignalLoginPurchaseStep.Finished(SignalLoginPurchaseResult.PurchaseUnavailable)
    val actions = collectActions()

    val state = applyEvent(
      SignalLoginPaymentState(price = SignalLoginPaymentState.Price.Available("$1.99")),
      SignalLoginPaymentScreenEvents.ContinueClicked
    )

    assertThat(actions).isEmpty()
    assertThat(state.dialogs.purchaseUnavailable).isTrue()
    assertThat(state.showSpinner).isFalse()
  }

  @Test
  fun `PurchaseFlowCompleted registers the account and navigates on success`() = runTest(testDispatcher) {
    val account = mockk<RegisteredAccountData>(relaxed = true)
    val purchaseResult = OneTimePurchaseResult.Success(OneTimePurchase("token", BillingPurchaseState.PURCHASED))
    coEvery { mockRepository.completeSignalLoginPurchase(purchaseResult) } returns SignalLoginPurchaseResult.Registered(account)
    val (events, emitter) = collectParentEvents()

    applyEvent(
      SignalLoginPaymentState(price = SignalLoginPaymentState.Price.Available("$1.99"), showSpinner = true),
      SignalLoginPaymentScreenEvents.PurchaseFlowCompleted(purchaseResult),
      emitter
    )

    coVerify(exactly = 1) { mockRepository.completeSignalLoginPurchase(purchaseResult) }
    assertThat(events.last()).isEqualTo(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.SignalLoginInfo, false))
  }

  @Test
  fun `PurchaseFlowCompleted leaves the state alone when the user cancels the purchase`() = runTest(testDispatcher) {
    coEvery { mockRepository.completeSignalLoginPurchase(any()) } returns SignalLoginPurchaseResult.Cancelled

    val state = applyEvent(
      SignalLoginPaymentState(price = SignalLoginPaymentState.Price.Available("$1.99"), showSpinner = true),
      SignalLoginPaymentScreenEvents.PurchaseFlowCompleted(OneTimePurchaseResult.UserCancelled)
    )

    assertThat(state.dialogs).isEqualTo(SignalLoginPaymentState.Dialogs())
    assertThat(state.showSpinner).isFalse()
  }

  @Test
  fun `PurchaseFlowCompleted remembers the purchase when payment has not settled`() = runTest(testDispatcher) {
    coEvery { mockRepository.completeSignalLoginPurchase(any()) } returns SignalLoginPurchaseResult.PurchasePending

    val state = applyEvent(
      SignalLoginPaymentState(price = SignalLoginPaymentState.Price.Available("$1.99")),
      SignalLoginPaymentScreenEvents.PurchaseFlowCompleted(OneTimePurchaseResult.GenericError)
    )

    assertThat(state.hasUnredeemedPurchase).isTrue()
    assertThat(state.dialogs.purchasePending).isTrue()
  }

  @Test
  fun `PurchaseFlowCompleted keeps the purchase around when the service will not redeem it`() = runTest(testDispatcher) {
    coEvery { mockRepository.completeSignalLoginPurchase(any()) } returns
      SignalLoginPurchaseResult.RedemptionFailed(CreateLoginReceiptCredentialError.PurchaseNotFound)

    val state = applyEvent(
      SignalLoginPaymentState(price = SignalLoginPaymentState.Price.Available("$1.99")),
      SignalLoginPaymentScreenEvents.PurchaseFlowCompleted(OneTimePurchaseResult.GenericError)
    )

    assertThat(state.hasUnredeemedPurchase).isTrue()
    assertThat(state.dialogs.purchaseFailed).isTrue()
  }

  @Test
  fun `PurchaseFlowCompleted reports an unavailable purchase, which is what a missing activity becomes`() = runTest(testDispatcher) {
    coEvery { mockRepository.completeSignalLoginPurchase(OneTimePurchaseResult.Unavailable) } returns
      SignalLoginPurchaseResult.PurchaseUnavailable

    val state = applyEvent(
      SignalLoginPaymentState(price = SignalLoginPaymentState.Price.Available("$1.99")),
      SignalLoginPaymentScreenEvents.PurchaseFlowCompleted(OneTimePurchaseResult.Unavailable)
    )

    assertThat(state.dialogs.purchaseUnavailable).isTrue()
  }
}
