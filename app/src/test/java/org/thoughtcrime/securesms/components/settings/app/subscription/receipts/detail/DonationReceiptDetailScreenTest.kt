/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import java.math.BigDecimal
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DonationReceiptDetailScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<DonationReceiptDetailEvent>()

  @Test
  fun `clicking share emits ShareClicked`() {
    setContent(createState())

    composeTestRule.onNodeWithTag(DonationReceiptDetailTestTags.BUTTON_SHARE).performClick()

    assertThat(events).containsExactly(DonationReceiptDetailEvent.ShareClicked)
  }

  @Test
  fun `clicking back emits NavigateBackClicked`() {
    setContent(createState())

    composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

    assertThat(events).containsExactly(DonationReceiptDetailEvent.NavigateBackClicked)
  }

  @Test
  fun `share is enabled once the receipt has loaded`() {
    setContent(createState())

    composeTestRule.onNodeWithTag(DonationReceiptDetailTestTags.BUTTON_SHARE).assertIsEnabled()
  }

  @Test
  fun `share is disabled while the receipt is loading`() {
    setContent(createState(record = null))

    composeTestRule.onNodeWithTag(DonationReceiptDetailTestTags.BUTTON_SHARE).assertIsNotEnabled()
  }

  @Test
  fun `share is disabled while the receipt image is rendering`() {
    setContent(createState(isRenderingReceipt = true))

    composeTestRule.onNodeWithTag(DonationReceiptDetailTestTags.BUTTON_SHARE).assertIsNotEnabled()
  }

  @Test
  fun `the receipt details are shown`() {
    setContent(createState())

    composeTestRule.onNodeWithText("Donation type").assertIsDisplayed()
    composeTestRule.onNodeWithText("Date paid").assertIsDisplayed()
  }

  private fun setContent(state: DonationReceiptDetailState) {
    composeTestRule.setContent {
      DonationReceiptDetailScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun createState(
    record: InAppPaymentReceiptRecord? = InAppPaymentReceiptRecord(
      amount = FiatMoney(BigDecimal.valueOf(5), Currency.getInstance("USD")),
      timestamp = 1760000000000L,
      type = InAppPaymentReceiptRecord.Type.RECURRING_DONATION,
      subscriptionLevel = 500
    ),
    isRenderingReceipt: Boolean = false
  ): DonationReceiptDetailState {
    return DonationReceiptDetailState(
      inAppPaymentReceiptRecord = record,
      isRenderingReceipt = isRenderingReceipt
    )
  }
}
