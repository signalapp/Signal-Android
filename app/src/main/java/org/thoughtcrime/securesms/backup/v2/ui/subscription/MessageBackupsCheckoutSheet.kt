/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.updateLayoutParams
import kotlinx.collections.immutable.persistentListOf
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.models.GooglePayButton
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.databinding.PaypalButtonBinding
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import java.math.BigDecimal
import java.util.Currency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageBackupsCheckoutSheet(
  messageBackupsType: MessageBackupsType,
  availablePaymentMethods: List<InAppPaymentData.PaymentMethodType>,
  sheetState: SheetState,
  onDismissRequest: () -> Unit,
  onPaymentMethodSelected: (InAppPaymentData.PaymentMethodType) -> Unit
) {
  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = sheetState,
    dragHandle = { BottomSheets.Handle() },
    modifier = Modifier.padding()
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
        .navigationBarsPadding()
    ) {
      SheetContent(
        messageBackupsType = messageBackupsType,
        availablePaymentGateways = availablePaymentMethods,
        onPaymentGatewaySelected = onPaymentMethodSelected
      )
    }
  }
}

@Composable
private fun SheetContent(
  messageBackupsType: MessageBackupsType,
  availablePaymentGateways: List<InAppPaymentData.PaymentMethodType>,
  onPaymentGatewaySelected: (InAppPaymentData.PaymentMethodType) -> Unit
) {
  val resources = LocalContext.current.resources
  val formattedPrice = remember(messageBackupsType.pricePerMonth) {
    FiatMoneyUtil.format(resources, messageBackupsType.pricePerMonth, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
  }

  Text(
    text = stringResource(id = R.string.MessageBackupsCheckoutSheet__pay_s_per_month, formattedPrice),
    style = MaterialTheme.typography.titleLarge,
    modifier = Modifier.padding(top = 48.dp)
  )

  Text(
    text = stringResource(id = R.string.MessageBackupsCheckoutSheet__youll_get),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 5.dp)
  )

  MessageBackupsTypeBlock(
    messageBackupsType = messageBackupsType,
    isCurrent = false,
    isSelected = false,
    onSelected = {},
    enabled = false,
    modifier = Modifier.padding(top = 24.dp)
  )

  Column(
    verticalArrangement = spacedBy(12.dp),
    modifier = Modifier.padding(top = 48.dp, bottom = 24.dp)
  ) {
    availablePaymentGateways.forEach {
      when (it) {
        InAppPaymentData.PaymentMethodType.GOOGLE_PAY -> GooglePayButton {
          onPaymentGatewaySelected(InAppPaymentData.PaymentMethodType.GOOGLE_PAY)
        }

        InAppPaymentData.PaymentMethodType.PAYPAL -> PayPalButton {
          onPaymentGatewaySelected(InAppPaymentData.PaymentMethodType.PAYPAL)
        }

        InAppPaymentData.PaymentMethodType.CARD -> CreditOrDebitCardButton {
          onPaymentGatewaySelected(InAppPaymentData.PaymentMethodType.CARD)
        }

        InAppPaymentData.PaymentMethodType.SEPA_DEBIT -> SepaButton {
          onPaymentGatewaySelected(InAppPaymentData.PaymentMethodType.SEPA_DEBIT)
        }

        InAppPaymentData.PaymentMethodType.IDEAL -> IdealButton {
          onPaymentGatewaySelected(InAppPaymentData.PaymentMethodType.IDEAL)
        }

        InAppPaymentData.PaymentMethodType.UNKNOWN -> error("Unsupported payment method type $it")
      }
    }
  }
}

@Composable
private fun PayPalButton(
  onClick: () -> Unit
) {
  AndroidView(factory = {
    val view = LayoutInflater.from(it).inflate(R.layout.paypal_button, null)
    view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    view
  }) {
    val binding = PaypalButtonBinding.bind(it)
    binding.paypalButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
      marginStart = 0
      marginEnd = 0
    }

    binding.paypalButton.setOnClickListener {
      onClick()
    }
  }
}

@Composable
private fun GooglePayButton(
  onClick: () -> Unit
) {
  val model = GooglePayButton.Model(onClick, true)

  AndroidView(factory = {
    LayoutInflater.from(it).inflate(R.layout.google_pay_button_pref, null)
  }) {
    val holder = GooglePayButton.ViewHolder(it)
    holder.bind(model)
  }
}

@Composable
private fun SepaButton(
  onClick: () -> Unit
) {
  Buttons.LargeTonal(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth()
  ) {
    Icon(
      painter = painterResource(id = R.drawable.bank_transfer),
      contentDescription = null,
      modifier = Modifier.padding(end = 8.dp)
    )

    Text(text = stringResource(id = R.string.GatewaySelectorBottomSheet__bank_transfer))
  }
}

@Composable
private fun IdealButton(
  onClick: () -> Unit
) {
  Buttons.LargeTonal(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth()
  ) {
    Image(
      painter = painterResource(id = R.drawable.logo_ideal),
      contentDescription = null,
      modifier = Modifier
        .size(32.dp)
        .padding(end = 8.dp)
    )

    Text(text = stringResource(id = R.string.GatewaySelectorBottomSheet__ideal))
  }
}

@Composable
private fun CreditOrDebitCardButton(
  onClick: () -> Unit
) {
  Buttons.LargePrimary(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth()
  ) {
    Icon(
      painter = painterResource(id = R.drawable.credit_card),
      contentDescription = null,
      modifier = Modifier.padding(end = 8.dp)
    )

    Text(
      text = stringResource(id = R.string.GatewaySelectorBottomSheet__credit_or_debit_card)
    )
  }
}

@Preview
@Composable
private fun MessageBackupsCheckoutSheetPreview() {
  val availablePaymentGateways = InAppPaymentData.PaymentMethodType.values().toList() - InAppPaymentData.PaymentMethodType.UNKNOWN

  Previews.Preview {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
    ) {
      SheetContent(
        messageBackupsType = MessageBackupsType(
          tier = MessageBackupTier.FREE,
          title = "Free",
          pricePerMonth = FiatMoney(BigDecimal.ZERO, Currency.getInstance("USD")),
          features = persistentListOf()
        ),
        availablePaymentGateways = availablePaymentGateways,
        onPaymentGatewaySelected = {}
      )
    }
  }
}
