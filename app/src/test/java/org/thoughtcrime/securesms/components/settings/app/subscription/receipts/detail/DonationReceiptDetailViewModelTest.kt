/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import android.net.Uri
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import java.math.BigDecimal
import java.util.Currency

@OptIn(ExperimentalCoroutinesApi::class)
class DonationReceiptDetailViewModelTest {

  companion object {
    private const val RECEIPT_ID = 7L

    private val RECORD = InAppPaymentReceiptRecord(
      amount = FiatMoney(BigDecimal.valueOf(5), Currency.getInstance("USD")),
      timestamp = 1760000000000L,
      type = InAppPaymentReceiptRecord.Type.RECURRING_DONATION,
      subscriptionLevel = 500
    )
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val receiptUri = mockk<Uri>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(DonationReceiptDetailRepository)

    coEvery { DonationReceiptDetailRepository.getDonationReceiptRecord(RECEIPT_ID) } returns RECORD
    coEvery { DonationReceiptDetailRepository.renderReceiptImage(RECORD) } returns receiptUri
  }

  @After
  fun tearDown() {
    unmockkObject(DonationReceiptDetailRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `the receipt is read out of the repository on creation`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(DonationReceiptDetailState(inAppPaymentReceiptRecord = RECORD))
    assertThat(viewModel.state.value.canShare).isTrue()
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(DonationReceiptDetailEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(DonationReceiptDetailAction.NavigateBack)
  }

  @Test
  fun `ShareClicked renders the receipt and shares it`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(DonationReceiptDetailEvent.ShareClicked)

    assertThat(actions).containsExactly(DonationReceiptDetailAction.ShareReceipt(receiptUri))
    assertThat(viewModel.state.value.isRenderingReceipt).isFalse()
  }

  @Test
  fun `ShareClicked shows rendering progress until the image is ready`() = runTest(testDispatcher) {
    val rendered = CompletableDeferred<Uri>()
    coEvery { DonationReceiptDetailRepository.renderReceiptImage(RECORD) } coAnswers { rendered.await() }

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(DonationReceiptDetailEvent.ShareClicked)

    assertThat(viewModel.state.value.isRenderingReceipt).isTrue()
    assertThat(viewModel.state.value.canShare).isFalse()
    assertThat(actions).isEmpty()

    rendered.complete(receiptUri)

    assertThat(viewModel.state.value.isRenderingReceipt).isFalse()
    assertThat(actions).containsExactly(DonationReceiptDetailAction.ShareReceipt(receiptUri))
  }

  @Test
  fun `a second ShareClicked while rendering does not render again`() = runTest(testDispatcher) {
    val rendered = CompletableDeferred<Uri>()
    coEvery { DonationReceiptDetailRepository.renderReceiptImage(RECORD) } coAnswers { rendered.await() }

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(DonationReceiptDetailEvent.ShareClicked)
    viewModel.onEvent(DonationReceiptDetailEvent.ShareClicked)
    rendered.complete(receiptUri)

    coVerify(exactly = 1) { DonationReceiptDetailRepository.renderReceiptImage(RECORD) }
    assertThat(actions).containsExactly(DonationReceiptDetailAction.ShareReceipt(receiptUri))
  }

  @Test
  fun `ShareClicked before the receipt has loaded does nothing`() = runTest(testDispatcher) {
    val loaded = CompletableDeferred<InAppPaymentReceiptRecord>()
    coEvery { DonationReceiptDetailRepository.getDonationReceiptRecord(RECEIPT_ID) } coAnswers { loaded.await() }

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(DonationReceiptDetailEvent.ShareClicked)

    coVerify(exactly = 0) { DonationReceiptDetailRepository.renderReceiptImage(any()) }
    assertThat(viewModel.state.value.isRenderingReceipt).isFalse()
    assertThat(actions).isEmpty()
  }

  private fun createViewModel(): DonationReceiptDetailViewModel = DonationReceiptDetailViewModel(RECEIPT_ID)

  private fun TestScope.collectActions(actions: Flow<DonationReceiptDetailAction>): List<DonationReceiptDetailAction> {
    val collected = mutableListOf<DonationReceiptDetailAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
