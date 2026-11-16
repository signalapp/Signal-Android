/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.core.models.badges.Badge
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.TabbedPager
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.util.money.FiatMoney
import org.signal.core.util.toLocalDateTime
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImageMedium
import org.thoughtcrime.securesms.components.settings.app.subscription.receipts.labelRes
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import java.math.BigDecimal
import java.util.Currency

@VisibleForTesting
object DonationReceiptListTestTags {
  const val EMPTY = "empty"
  private const val LIST_PREFIX = "list-"
  private const val RECEIPT_PREFIX = "receipt-"

  fun list(tab: DonationReceiptListState.Tab): String = LIST_PREFIX + tab.name
  fun receipt(receiptId: Long): String = RECEIPT_PREFIX + receiptId
}

/**
 * Every donation receipt, split into tabs by kind of donation and grouped by year.
 */
@Composable
fun DonationReceiptListScreen(
  state: DonationReceiptListState,
  onEvent: (DonationReceiptListEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val tabs = DonationReceiptListState.Tab.entries

  Scaffolds.Settings(
    title = stringResource(R.string.DonationReceiptListFragment__all_activity),
    onNavigationClick = { onEvent(DonationReceiptListEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    modifier = modifier
  ) { contentPadding ->
    TabbedPager(
      tabTitles = tabs.map { stringResource(it.title) },
      scrollable = true,
      modifier = Modifier.padding(contentPadding)
    ) { page ->
      val tab = tabs[page]
      val receipts = state.receiptsFor(tab)

      when {
        receipts == null -> Unit
        receipts.isEmpty() -> EmptyState()
        else -> ReceiptList(tab = tab, receipts = receipts, badgeFor = state::badgeFor, onEvent = onEvent)
      }
    }
  }
}

@Composable
private fun ReceiptList(
  tab: DonationReceiptListState.Tab,
  receipts: List<InAppPaymentReceiptRecord>,
  badgeFor: (InAppPaymentReceiptRecord) -> Badge?,
  onEvent: (DonationReceiptListEvent) -> Unit
) {
  val receiptsByYear = remember(receipts) { receipts.groupBy { it.timestamp.toLocalDateTime().year } }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag(DonationReceiptListTestTags.list(tab))
  ) {
    receiptsByYear.forEach { (year, yearReceipts) ->
      stickyHeader(key = "year-$year") {
        Texts.SectionHeader(
          text = year.toString(),
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
        )
      }

      items(yearReceipts, key = { it.id }) { receipt ->
        ReceiptRow(
          receipt = receipt,
          badge = badgeFor(receipt),
          onClick = { onEvent(DonationReceiptListEvent.ReceiptClicked(receipt.id)) }
        )
      }
    }

    item(key = "reinstall-note") {
      Text(
        text = stringResource(R.string.DonationReceiptListFragment__if_you_have),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .horizontalGutters()
          .padding(vertical = 16.dp)
      )
    }
  }
}

@Composable
private fun ReceiptRow(
  receipt: InAppPaymentReceiptRecord,
  badge: Badge?,
  onClick: () -> Unit
) {
  val locale = LocalLocale.current.platformLocale
  val resources = LocalResources.current
  val date = remember(locale, receipt.timestamp) { DateUtils.formatDate(locale, receipt.timestamp) }
  val amount = remember(resources, receipt.amount) { FiatMoneyUtil.format(resources, receipt.amount) }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(start = 18.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
      .testTag(DonationReceiptListTestTags.receipt(receipt.id))
  ) {
    BadgeImageMedium(badge = badge, modifier = Modifier.size(32.dp))

    // The amount sits beside the date where it fits, and drops below it where it doesn't.
    FlowRow(
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .weight(1f)
        .padding(start = 14.dp)
    ) {
      Column(modifier = Modifier.padding(end = 14.dp)) {
        Text(text = date, style = MaterialTheme.typography.bodyLarge)
        Text(
          text = stringResource(receipt.type.labelRes),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Text(text = amount, style = MaterialTheme.typography.bodyLarge)
    }
  }
}

@Composable
private fun EmptyState() {
  Column(
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .horizontalGutters()
      .testTag(DonationReceiptListTestTags.EMPTY)
  ) {
    Text(
      text = stringResource(R.string.DonationReceiptListFragment__no_receipts),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )

    Text(
      text = stringResource(R.string.DonationReceiptListFragment__if_you_have),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 8.dp)
    )
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun DonationReceiptListScreenPreview() {
  DonationReceiptListScreen(
    state = DonationReceiptListState(
      receipts = listOf(
        previewReceipt(1L, InAppPaymentReceiptRecord.Type.RECURRING_DONATION, timestamp = 1_760_000_000_000L),
        previewReceipt(2L, InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION, timestamp = 1_740_000_000_000L),
        previewReceipt(3L, InAppPaymentReceiptRecord.Type.ONE_TIME_GIFT, timestamp = 1_700_000_000_000L)
      )
    ),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun DonationReceiptListScreenEmptyPreview() {
  DonationReceiptListScreen(state = DonationReceiptListState(receipts = emptyList()), onEvent = {})
}

private fun previewReceipt(id: Long, type: InAppPaymentReceiptRecord.Type, timestamp: Long): InAppPaymentReceiptRecord {
  return InAppPaymentReceiptRecord(
    id = id,
    amount = FiatMoney(BigDecimal.TEN, Currency.getInstance("USD")),
    timestamp = timestamp,
    type = type,
    subscriptionLevel = 500
  )
}
