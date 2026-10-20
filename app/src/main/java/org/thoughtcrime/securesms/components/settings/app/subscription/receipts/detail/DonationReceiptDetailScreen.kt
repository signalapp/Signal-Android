/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.receipts.labelRes
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import java.math.BigDecimal
import java.util.Currency
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object DonationReceiptDetailTestTags {
  const val SCROLLER = "scroller"
  const val BUTTON_SHARE = "button-share"
}

/**
 * The details of a single donation receipt, with a button to share it as an image.
 */
@Composable
fun DonationReceiptDetailScreen(
  state: DonationReceiptDetailState,
  onEvent: (DonationReceiptDetailEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = "",
    onNavigationClick = { onEvent(DonationReceiptDetailEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    modifier = modifier
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .testTag(DonationReceiptDetailTestTags.SCROLLER)
      ) {
        if (state.inAppPaymentReceiptRecord != null) {
          receiptDetails(state.inAppPaymentReceiptRecord)
        }
      }

      Buttons.LargeTonal(
        onClick = { onEvent(DonationReceiptDetailEvent.ShareClicked) },
        enabled = state.canShare,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 48.dp, vertical = 16.dp)
          .testTag(DonationReceiptDetailTestTags.BUTTON_SHARE)
      ) {
        Text(text = stringResource(R.string.DonationReceiptDetailsFragment__share_receipt))
      }
    }
  }

  if (state.isRenderingReceipt) {
    Dialogs.IndeterminateProgressDialog()
  }
}

private fun LazyListScope.receiptDetails(record: InAppPaymentReceiptRecord) {
  item {
    Image(
      painter = painterResource(CoreUiR.drawable.image_signal_logo_wordmark),
      contentDescription = null,
      modifier = Modifier.fillMaxWidth()
    )
  }

  item {
    val resources = LocalResources.current
    val amount = remember(resources, record.amount) { FiatMoneyUtil.format(resources, record.amount) }

    Text(
      text = amount,
      style = MaterialTheme.typography.displayMedium,
      textAlign = TextAlign.Center,
      maxLines = 1,
      softWrap = false,
      autoSize = TextAutoSize.StepBased(
        minFontSize = 24.sp,
        maxFontSize = MaterialTheme.typography.displayMedium.fontSize
      ),
      modifier = Modifier
        .fillMaxWidth()
        .horizontalGutters()
        .padding(vertical = 16.dp)
    )
  }

  item {
    Dividers.Default()
  }

  item {
    Rows.TextRow(
      text = stringResource(R.string.DonationReceiptDetailsFragment__donation_type),
      label = stringResource(record.type.labelRes)
    )
  }

  item {
    val locale = LocalLocale.current.platformLocale
    val datePaid = remember(locale, record.timestamp) { DateUtils.formatDateWithYear(locale, record.timestamp) }

    Rows.TextRow(
      text = stringResource(R.string.DonationReceiptDetailsFragment__date_paid),
      label = datePaid
    )
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun DonationReceiptDetailScreenPreview() {
  DonationReceiptDetailScreen(
    state = DonationReceiptDetailState(
      inAppPaymentReceiptRecord = InAppPaymentReceiptRecord(
        amount = FiatMoney(BigDecimal.valueOf(5), Currency.getInstance("USD")),
        timestamp = 1760000000000L,
        type = InAppPaymentReceiptRecord.Type.RECURRING_DONATION,
        subscriptionLevel = 500
      )
    ),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun DonationReceiptDetailScreenLoadingPreview() {
  DonationReceiptDetailScreen(
    state = DonationReceiptDetailState(),
    onEvent = {}
  )
}
