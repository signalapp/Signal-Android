/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups.type

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.Previews
import org.signal.core.ui.Rows
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsFlowActivity
import org.thoughtcrime.securesms.backup.v2.ui.subscription.getTierDetails
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.viewModel
import java.util.Locale

/**
 * Allows the user to modify their backup plan
 */
class BackupsTypeSettingsFragment : ComposeFragment() {

  private val viewModel: BackupsTypeSettingsViewModel by viewModel {
    BackupsTypeSettingsViewModel()
  }

  @Composable
  override fun FragmentContent() {
    val contentCallbacks = remember {
      Callbacks()
    }

    val state by viewModel.state

    BackupsTypeSettingsContent(
      state = state,
      contentCallbacks = contentCallbacks
    )
  }

  private inner class Callbacks : ContentCallbacks {
    override fun onNavigationClick() {
      findNavController().popBackStack()
    }

    override fun onPaymentHistoryClick() {
      // TODO [message-backups] Navigate to payment history
    }

    override fun onChangeOrCancelSubscriptionClick() {
      startActivity(Intent(requireContext(), MessageBackupsFlowActivity::class.java))
    }
  }
}

private interface ContentCallbacks {
  fun onNavigationClick() = Unit
  fun onPaymentHistoryClick() = Unit
  fun onChangeOrCancelSubscriptionClick() = Unit
}

@Composable
private fun BackupsTypeSettingsContent(
  state: BackupsTypeSettingsState,
  contentCallbacks: ContentCallbacks
) {
  if (state.backupsTier == null) {
    return
  }

  Scaffolds.Settings(
    title = "Backup Type",
    onNavigationClick = contentCallbacks::onNavigationClick,
    navigationIconPainter = painterResource(id = R.drawable.symbol_arrow_left_24)
  ) {
    LazyColumn(
      modifier = Modifier.padding(it)
    ) {
      item {
        BackupsTypeRow(
          backupsTier = state.backupsTier,
          nextRenewalTimestamp = state.nextRenewalTimestamp
        )
      }

      item {
        PaymentSourceRow(
          paymentSourceType = state.paymentSourceType
        )
      }

      item {
        Rows.TextRow(
          text = "Change or cancel subscription", // TODO [message-backups] final copy
          onClick = contentCallbacks::onChangeOrCancelSubscriptionClick
        )
      }

      item {
        Rows.TextRow(
          text = "Payment history", // TODO [message-backups] final copy
          onClick = contentCallbacks::onPaymentHistoryClick
        )
      }
    }
  }
}

@Composable
private fun BackupsTypeRow(
  backupsTier: MessageBackupTier,
  nextRenewalTimestamp: Long
) {
  val messageBackupsType = remember {
    getTierDetails(backupsTier)
  }

  val resources = LocalContext.current.resources
  val formattedAmount = remember(messageBackupsType.pricePerMonth) {
    FiatMoneyUtil.format(resources, messageBackupsType.pricePerMonth, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
  }

  val renewal = remember(nextRenewalTimestamp) {
    DateUtils.formatDateWithoutDayOfWeek(Locale.getDefault(), nextRenewalTimestamp)
  }

  Rows.TextRow(text = {
    Column {
      Text(text = messageBackupsType.title)
      Text(
        text = "$formattedAmount/month . Renews $renewal", // TODO [message-backups] final copy
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  })
}

@Composable
private fun PaymentSourceRow(paymentSourceType: PaymentSourceType) {
  val paymentSourceTextResId = remember(paymentSourceType) {
    when (paymentSourceType) {
      is PaymentSourceType.Stripe.CreditCard -> R.string.BackupsTypeSettingsFragment__credit_or_debit_card
      is PaymentSourceType.Stripe.IDEAL -> R.string.BackupsTypeSettingsFragment__iDEAL
      is PaymentSourceType.Stripe.GooglePay -> R.string.BackupsTypeSettingsFragment__google_pay
      is PaymentSourceType.Stripe.SEPADebit -> R.string.BackupsTypeSettingsFragment__bank_transfer
      is PaymentSourceType.PayPal -> R.string.BackupsTypeSettingsFragment__paypal
      is PaymentSourceType.Unknown -> R.string.BackupsTypeSettingsFragment__unknown
    }
  }

  Rows.TextRow(text = {
    Column {
      Text(text = "Payment method") // TOD [message-backups] Final copy
      Text(
        text = stringResource(id = paymentSourceTextResId),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  })
}

@SignalPreview
@Composable
private fun BackupsTypeSettingsContentPreview() {
  Previews.Preview {
    BackupsTypeSettingsContent(
      state = BackupsTypeSettingsState(
        backupsTier = MessageBackupTier.PAID
      ),
      contentCallbacks = object : ContentCallbacks {}
    )
  }
}
