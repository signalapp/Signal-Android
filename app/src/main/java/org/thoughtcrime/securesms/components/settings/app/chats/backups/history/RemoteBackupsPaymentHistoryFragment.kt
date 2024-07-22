/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups.history

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.navArgument
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Dividers
import org.signal.core.ui.Previews
import org.signal.core.ui.Rows
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.Texts
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.receipts.ReceiptImageRenderer
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.Nav
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import java.math.BigDecimal
import java.util.Calendar
import java.util.Currency
import java.util.Locale

/**
 * Displays a list or detail view of in-app-payment receipts related to
 * backups.
 */
class RemoteBackupsPaymentHistoryFragment : ComposeFragment() {

  private val viewModel: RemoteBackupsPaymentHistoryViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
      navController.setOnBackPressedDispatcher(requireActivity().onBackPressedDispatcher)
      navController.enableOnBackPressed(true)
    }

    val onNavigationClick = remember {
      {
        if (!navController.popBackStack()) {
          findNavController().popBackStack()
        }
      }
    }

    Nav.Host(navController = navController, startDestination = "list") {
      composable("list") {
        PaymentHistoryContent(
          state = state,
          onNavigationClick = onNavigationClick,
          onRecordClick = { navController.navigate("detail/${it.id}") }
        )
      }

      composable("detail/{recordId}", listOf(navArgument("recordId") { type = NavType.LongType })) { backStackEntry ->
        val recordId = backStackEntry.arguments?.getLong("recordId")!!
        val record = state.records[recordId]!!

        PaymentHistoryDetails(
          record = record,
          onNavigationClick = onNavigationClick,
          onShareClick = this@RemoteBackupsPaymentHistoryFragment::onShareClick
        )

        if (state.displayProgressDialog) {
          Dialogs.IndeterminateProgressDialog()
        }
      }
    }
  }

  private fun onShareClick(record: InAppPaymentReceiptRecord) {
    viewModel.onStartRenderingBitmap()
    ReceiptImageRenderer.renderPng(
      requireContext(),
      viewLifecycleOwner,
      record,
      getString(R.string.RemoteBackupsPaymentHistoryFragment__text_and_all_media_backup),
      object : ReceiptImageRenderer.Callback {
        override fun onBitmapRendered() {
          viewModel.onEndRenderingBitmap()
        }

        override fun onStartActivity(intent: Intent) {
          startActivity(intent)
        }
      }
    )
  }
}

@Composable
private fun PaymentHistoryContent(
  state: RemoteBackupsPaymentHistoryState,
  onNavigationClick: () -> Unit,
  onRecordClick: (InAppPaymentReceiptRecord) -> Unit
) {
  Scaffolds.Settings(
    title = stringResource(id = R.string.RemoteBackupsPaymentHistoryFragment__payment_history),
    navigationIconPainter = painterResource(id = R.drawable.symbol_arrow_left_24),
    onNavigationClick = onNavigationClick
  ) {
    val itemList = remember(state.records) { state.records.values.toPersistentList() }

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(it)
    ) {
      itemsIndexed(
        items = itemList,
        key = { _, item -> item.id }
      ) { idx, item ->
        val previous = itemList.getOrNull(idx - 1)
        val previousYear = rememberYear(timestamp = previous?.timestamp ?: 0)
        val ourYear = rememberYear(timestamp = item.timestamp)

        if (previousYear != ourYear) {
          Texts.SectionHeader(text = "$ourYear")
        }

        PaymentHistoryRow(item, onRecordClick)
      }
    }
  }
}

@Composable
private fun rememberYear(timestamp: Long): Int {
  if (timestamp == 0L) {
    return -1
  }

  val calendar = remember {
    Calendar.getInstance()
  }

  return remember(timestamp) {
    calendar.timeInMillis = timestamp
    calendar.get(Calendar.YEAR)
  }
}

@Composable
private fun PaymentHistoryRow(
  record: InAppPaymentReceiptRecord,
  onRecordClick: (InAppPaymentReceiptRecord) -> Unit
) {
  val date = remember(record.timestamp) {
    DateUtils.formatDateWithYear(Locale.getDefault(), record.timestamp)
  }

  val onClick = remember(record) {
    { onRecordClick(record) }
  }

  Rows.TextRow(text = {
    Column(
      modifier = Modifier.weight(1f)
    ) {
      Text(
        text = date,
        style = MaterialTheme.typography.bodyLarge
      )

      Text(
        text = stringResource(id = R.string.RemoteBackupsPaymentHistoryFragment__text_and_all_media_backup),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    val resources = LocalContext.current.resources
    val fiat = remember(record.amount) {
      FiatMoneyUtil.format(resources, record.amount, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
    }

    Text(text = fiat)
  }, onClick = onClick)
}

@Composable
private fun PaymentHistoryDetails(
  record: InAppPaymentReceiptRecord,
  onNavigationClick: () -> Unit,
  onShareClick: (InAppPaymentReceiptRecord) -> Unit
) {
  Scaffolds.Settings(
    title = stringResource(id = R.string.RemoteBackupsPaymentHistoryFragment__payment_details),
    onNavigationClick = onNavigationClick,
    navigationIconPainter = painterResource(id = R.drawable.symbol_arrow_left_24)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(it)
    ) {
      val resources = LocalContext.current.resources
      val formattedAmount = remember(record.amount) {
        FiatMoneyUtil.format(resources, record.amount, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
      }

      Image(
        painter = painterResource(id = R.drawable.ic_signal_logo_type),
        contentDescription = null,
        modifier = Modifier
          .align(alignment = Alignment.CenterHorizontally)
          .padding(top = 24.dp, bottom = 16.dp)
      )

      Text(
        text = formattedAmount,
        style = MaterialTheme.typography.displayMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )

      Dividers.Default()

      Rows.TextRow(
        text = stringResource(id = R.string.RemoteBackupsPaymentHistoryFragment__backup_type),
        label = stringResource(id = R.string.RemoteBackupsPaymentHistoryFragment__text_and_all_media_backup)
      )

      val formattedDate = remember(record.timestamp) {
        DateUtils.formatDateWithYear(Locale.getDefault(), record.timestamp)
      }

      Rows.TextRow(
        text = stringResource(id = R.string.RemoteBackupsPaymentHistoryFragment__date_paid),
        label = formattedDate
      )

      Spacer(modifier = Modifier.weight(1f))

      Buttons.LargePrimary(
        onClick = { onShareClick(record) },
        modifier = Modifier
          .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
          .padding(bottom = 24.dp)
          .fillMaxWidth()
      ) {
        Text(text = stringResource(id = R.string.RemoteBackupsPaymentHistoryFragment__share))
      }
    }
  }
}

@SignalPreview
@Composable
private fun PaymentHistoryContentPreview() {
  Previews.Preview {
    PaymentHistoryContent(
      state = RemoteBackupsPaymentHistoryState(
        records = persistentMapOf(
          1L to testRecord()
        )
      ),
      onNavigationClick = {},
      onRecordClick = {}
    )
  }
}

@SignalPreview
@Composable
private fun PaymentHistoryRowPreview() {
  Previews.Preview {
    PaymentHistoryRow(
      record = testRecord(),
      onRecordClick = {}
    )
  }
}

@SignalPreview
@Composable
private fun PaymentDetailsContentPreview() {
  Previews.Preview {
    PaymentHistoryDetails(
      record = testRecord(),
      onNavigationClick = {},
      onShareClick = {}
    )
  }
}

private fun testRecord(): InAppPaymentReceiptRecord {
  return InAppPaymentReceiptRecord(
    id = 1,
    amount = FiatMoney(BigDecimal.ONE, Currency.getInstance("USD")),
    timestamp = 1718739691000,
    type = InAppPaymentReceiptRecord.Type.RECURRING_BACKUP,
    subscriptionLevel = 201
  )
}
