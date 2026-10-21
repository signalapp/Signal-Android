/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.compose.TabbedPagerTestTags
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import java.math.BigDecimal
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DonationReceiptListScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<DonationReceiptListEvent>()

  private val recurring = receipt(1L, InAppPaymentReceiptRecord.Type.RECURRING_DONATION)
  private val gift = receipt(3L, InAppPaymentReceiptRecord.Type.ONE_TIME_GIFT)

  @Test
  fun `nothing is shown until the receipts load`() {
    setContent(DonationReceiptListState())

    composeTestRule.onNodeWithTag(DonationReceiptListTestTags.EMPTY).assertDoesNotExist()
    composeTestRule.onNodeWithTag(DonationReceiptListTestTags.list(DonationReceiptListState.Tab.ALL)).assertDoesNotExist()
  }

  @Test
  fun `no receipts shows the empty state`() {
    setContent(DonationReceiptListState(receipts = emptyList()))

    composeTestRule.onNodeWithText("No receipts").assertExists()
  }

  @Test
  fun `clicking a receipt emits ReceiptClicked`() {
    setContent(DonationReceiptListState(receipts = listOf(recurring, gift)))

    composeTestRule.onNodeWithTag(DonationReceiptListTestTags.receipt(gift.id)).performClick()

    assertThat(events).containsExactly(DonationReceiptListEvent.ReceiptClicked(gift.id))
  }

  @Test
  fun `receipts are grouped under their year`() {
    setContent(DonationReceiptListState(receipts = listOf(recurring)))

    composeTestRule.onNodeWithText("2025").assertExists()
  }

  @Test
  fun `a tab shows only its kind of donation`() {
    setContent(DonationReceiptListState(receipts = listOf(recurring, gift)))

    composeTestRule.onNodeWithTag(TabbedPagerTestTags.tab(DonationReceiptListState.Tab.FOR_A_FRIEND.ordinal)).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(DonationReceiptListTestTags.list(DonationReceiptListState.Tab.FOR_A_FRIEND)).assertExists()
    composeTestRule.onNodeWithTag(DonationReceiptListTestTags.receipt(gift.id)).assertExists()
    composeTestRule.onNodeWithTag(DonationReceiptListTestTags.receipt(recurring.id)).assertDoesNotExist()
  }

  @Test
  fun `clicking back emits NavigateBackClicked`() {
    setContent(DonationReceiptListState(receipts = emptyList()))

    composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

    assertThat(events).containsExactly(DonationReceiptListEvent.NavigateBackClicked)
  }

  private fun setContent(state: DonationReceiptListState) {
    composeTestRule.setContent {
      DonationReceiptListScreen(state = state, onEvent = { events += it })
    }
  }

  private fun receipt(id: Long, type: InAppPaymentReceiptRecord.Type): InAppPaymentReceiptRecord {
    return InAppPaymentReceiptRecord(id = id, amount = FiatMoney(BigDecimal.TEN, Currency.getInstance("USD")), timestamp = 1_760_000_000_000L, type = type, subscriptionLevel = 500)
  }
}
