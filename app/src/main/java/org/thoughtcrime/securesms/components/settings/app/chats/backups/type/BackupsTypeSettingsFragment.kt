/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups.type

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.fragment.findNavController
import kotlinx.collections.immutable.persistentListOf
import org.signal.core.ui.Previews
import org.signal.core.ui.Rows
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.CheckoutFlowActivity
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.viewModel
import java.math.BigDecimal
import java.util.Currency
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

    val state by viewModel.state.collectAsState()

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
      findNavController().safeNavigate(R.id.action_backupsTypeSettingsFragment_to_remoteBackupsPaymentHistoryFragment)
    }

    override fun onChangeOrCancelSubscriptionClick() {
      startActivity(CheckoutFlowActivity.createIntent(requireContext(), InAppPaymentType.RECURRING_BACKUP))
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
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
  if (state.messageBackupsType == null) {
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
          messageBackupsType = state.messageBackupsType,
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
          text = stringResource(id = R.string.BackupsTypeSettingsFragment__change_or_cancel_subscription),
          onClick = contentCallbacks::onChangeOrCancelSubscriptionClick
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(id = R.string.BackupsTypeSettingsFragment__payment_history),
          onClick = contentCallbacks::onPaymentHistoryClick
        )
      }
    }
  }
}

@Composable
private fun BackupsTypeRow(
  messageBackupsType: MessageBackupsType,
  nextRenewalTimestamp: Long
) {
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
        text = stringResource(id = R.string.BackupsTypeSettingsFragment__s_month_renews_s, formattedAmount, renewal),
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
        messageBackupsType = MessageBackupsType(
          tier = MessageBackupTier.FREE,
          pricePerMonth = FiatMoney(BigDecimal.ZERO, Currency.getInstance("USD")),
          title = "Free",
          features = persistentListOf()
        )
      ),
      contentCallbacks = object : ContentCallbacks {}
    )
  }
}
