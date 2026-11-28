/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.signal.core.models.badges.Badge
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import java.math.BigDecimal
import java.util.Currency

@OptIn(ExperimentalCoroutinesApi::class)
class DonationReceiptListViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val connectivity = MutableSharedFlow<Boolean>()
  private val recurring = receipt(1L, InAppPaymentReceiptRecord.Type.RECURRING_DONATION, level = 500)
  private val oneTime = receipt(2L, InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION)
  private val gift = receipt(3L, InAppPaymentReceiptRecord.Type.ONE_TIME_GIFT)

  private val levelBadge = mockk<Badge>()
  private val boostBadge = mockk<Badge>()
  private val giftBadge = mockk<Badge>()
  private val badges = listOf(
    DonationReceiptBadge(InAppPaymentReceiptRecord.Type.RECURRING_DONATION, 500, levelBadge),
    DonationReceiptBadge(InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION, -1, boostBadge),
    DonationReceiptBadge(InAppPaymentReceiptRecord.Type.ONE_TIME_GIFT, -1, giftBadge)
  )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(DonationReceiptListRepository)

    coEvery { DonationReceiptListRepository.getReceipts() } returns listOf(recurring, oneTime, gift)
    every { DonationReceiptListRepository.observeIsConnected() } returns connectivity
    coEvery { DonationReceiptListRepository.getBadges() } returns badges
  }

  @After
  fun tearDown() {
    unmockkObject(DonationReceiptListRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `the receipts and badges are read on start`() = runTest(testDispatcher) {
    val viewModel = DonationReceiptListViewModel()

    assertThat(viewModel.state.value).isEqualTo(DonationReceiptListState(receipts = listOf(recurring, oneTime, gift), badges = badges))
  }

  @Test
  fun `badges that failed to fetch are fetched again once back online`() = runTest(testDispatcher) {
    coEvery { DonationReceiptListRepository.getBadges() } returnsMany listOf(emptyList(), badges)
    val viewModel = DonationReceiptListViewModel()
    assertThat(viewModel.state.value.badges).isEqualTo(emptyList())

    connectivity.emit(true)

    assertThat(viewModel.state.value.badges).isEqualTo(badges)
  }

  @Test
  fun `badges are not fetched again once they have been`() = runTest(testDispatcher) {
    DonationReceiptListViewModel()

    connectivity.emit(false)
    connectivity.emit(true)

    coVerify(exactly = 1) { DonationReceiptListRepository.getBadges() }
  }

  @Test
  fun `each tab shows its kind of donation`() {
    val state = DonationReceiptListState(receipts = listOf(recurring, oneTime, gift))

    assertThat(state.receiptsFor(DonationReceiptListState.Tab.ALL)).isEqualTo(listOf(recurring, oneTime, gift))
    assertThat(state.receiptsFor(DonationReceiptListState.Tab.RECURRING)).isEqualTo(listOf(recurring))
    assertThat(state.receiptsFor(DonationReceiptListState.Tab.ONE_TIME)).isEqualTo(listOf(oneTime))
    assertThat(state.receiptsFor(DonationReceiptListState.Tab.FOR_A_FRIEND)).isEqualTo(listOf(gift))
    assertThat(DonationReceiptListState().receiptsFor(DonationReceiptListState.Tab.ALL)).isNull()
  }

  @Test
  fun `each receipt gets the badge for its kind of donation or subscription level`() {
    val state = DonationReceiptListState(badges = badges)

    assertThat(state.badgeFor(recurring)).isEqualTo(levelBadge)
    assertThat(state.badgeFor(oneTime)).isEqualTo(boostBadge)
    assertThat(state.badgeFor(gift)).isEqualTo(giftBadge)
    assertThat(state.badgeFor(receipt(4L, InAppPaymentReceiptRecord.Type.RECURRING_DONATION, level = 9999))).isNull()
  }

  @Test
  fun `clicking a receipt opens it`() = runTest(testDispatcher) {
    val viewModel = DonationReceiptListViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(DonationReceiptListEvent.ReceiptClicked(2L))

    assertThat(actions).containsExactly(DonationReceiptListAction.NavigateToReceipt(2L))
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = DonationReceiptListViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(DonationReceiptListEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(DonationReceiptListAction.NavigateBack)
  }

  private fun receipt(id: Long, type: InAppPaymentReceiptRecord.Type, level: Int = -1): InAppPaymentReceiptRecord {
    return InAppPaymentReceiptRecord(id = id, amount = FiatMoney(BigDecimal.TEN, Currency.getInstance("USD")), timestamp = 1_760_000_000_000L, type = type, subscriptionLevel = level)
  }

  private fun TestScope.collectActions(actions: Flow<DonationReceiptListAction>): List<DonationReceiptListAction> {
    val collected = mutableListOf<DonationReceiptListAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
