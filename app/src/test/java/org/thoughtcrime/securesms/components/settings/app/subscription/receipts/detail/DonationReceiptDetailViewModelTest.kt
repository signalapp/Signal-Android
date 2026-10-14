/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import android.content.Context
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
import org.thoughtcrime.securesms.components.settings.app.subscription.receipts.ReceiptImageRenderer
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import java.math.BigDecimal
import java.util.Currency

@OptIn(ExperimentalCoroutinesApi::class)
class DonationReceiptDetailViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val context: Context = mockk()
  private val uri: Uri = mockk()

  private val record = InAppPaymentReceiptRecord(
    id = RECORD_ID,
    amount = FiatMoney(BigDecimal.valueOf(5), Currency.getInstance("USD")),
    timestamp = 1760000000000L,
    type = InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION,
    subscriptionLevel = -1
  )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    mockkObject(DonationReceiptDetailRepository, ReceiptImageRenderer)
    coEvery { DonationReceiptDetailRepository.getDonationReceiptRecord(RECORD_ID) } returns record
    coEvery { ReceiptImageRenderer.renderPng(any(), any()) } returns uri
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkObject(DonationReceiptDetailRepository, ReceiptImageRenderer)
  }

  @Test
  fun `record is loaded out of the repository`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(DonationReceiptDetailState(inAppPaymentReceiptRecord = record))
  }

  @Test
  fun `shareReceipt is ignored before the record loads`() = runTest(testDispatcher) {
    val recordGate = CompletableDeferred<InAppPaymentReceiptRecord>()
    coEvery { DonationReceiptDetailRepository.getDonationReceiptRecord(RECORD_ID) } coAnswers { recordGate.await() }

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.shareReceipt(context)

    assertThat(viewModel.state.value.isRenderingReceipt).isFalse()
    assertThat(actions).isEmpty()
    coVerify(exactly = 0) { ReceiptImageRenderer.renderPng(any(), any()) }
  }

  @Test
  fun `shareReceipt marks the screen as rendering until the render completes`() = runTest(testDispatcher) {
    val renderGate = CompletableDeferred<Uri>()
    coEvery { ReceiptImageRenderer.renderPng(any(), any()) } coAnswers { renderGate.await() }

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.shareReceipt(context)

    assertThat(viewModel.state.value.isRenderingReceipt).isTrue()
    assertThat(actions).isEmpty()

    renderGate.complete(uri)

    assertThat(viewModel.state.value.isRenderingReceipt).isFalse()
    assertThat(actions).containsExactly(DonationReceiptDetailAction.ShareReceipt(uri))
  }

  @Test
  fun `a second shareReceipt during a render does not render twice`() = runTest(testDispatcher) {
    val renderGate = CompletableDeferred<Uri>()
    coEvery { ReceiptImageRenderer.renderPng(any(), any()) } coAnswers { renderGate.await() }

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.shareReceipt(context)
    viewModel.shareReceipt(context)
    renderGate.complete(uri)

    coVerify(exactly = 1) { ReceiptImageRenderer.renderPng(context, record) }
    assertThat(actions).containsExactly(DonationReceiptDetailAction.ShareReceipt(uri))
  }

  private fun createViewModel(): DonationReceiptDetailViewModel = DonationReceiptDetailViewModel(RECORD_ID)

  private fun TestScope.collectActions(actions: Flow<DonationReceiptDetailAction>): List<DonationReceiptDetailAction> {
    val collected = mutableListOf<DonationReceiptDetailAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }

  companion object {
    private const val RECORD_ID = 1L
  }
}
