/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.Buttons
import org.signal.core.ui.Rows
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.components.settings.app.internal.donor.DonationErrorValueCodeSelector
import org.thoughtcrime.securesms.components.settings.app.internal.donor.DonationErrorValueTypeSelector
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.database.model.databaseprotos.DonationErrorValue
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingOneTimeDonation
import org.thoughtcrime.securesms.database.model.databaseprotos.TerminalDonationQueue
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Configuration fragment for [TerminalDonationQueue.TerminalDonation]
 */
class InternalTerminalDonationConfigurationFragment : ComposeFragment() {

  @Composable
  override fun FragmentContent() {
    InternalTerminalDonationConfigurationContent(
      onAddClick = {
        SignalStore.inAppPayments.appendToTerminalDonationQueue(it)
        findNavController().popBackStack()
      }
    )
  }
}

@Preview
@Composable
private fun InternalTerminalDonationConfigurationContentPreview() {
  SignalTheme {
    Surface {
      InternalTerminalDonationConfigurationContent(
        onAddClick = {}
      )
    }
  }
}

@Composable
private fun InternalTerminalDonationConfigurationContent(
  onAddClick: (TerminalDonationQueue.TerminalDonation) -> Unit
) {
  val terminalDonationState: MutableState<TerminalDonationQueue.TerminalDonation> = remember {
    mutableStateOf(
      TerminalDonationQueue.TerminalDonation(
        level = 1000L,
        isLongRunningPaymentMethod = true
      )
    )
  }

  val paymentMethodType = remember(terminalDonationState.value.isLongRunningPaymentMethod) {
    if (terminalDonationState.value.isLongRunningPaymentMethod) PendingOneTimeDonation.PaymentMethodType.SEPA_DEBIT else PendingOneTimeDonation.PaymentMethodType.CARD
  }

  LazyColumn(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    item {
      Rows.ToggleRow(
        checked = terminalDonationState.value.isLongRunningPaymentMethod,
        text = "Long-running payment method",
        onCheckChanged = {
          terminalDonationState.value = terminalDonationState.value.copy(isLongRunningPaymentMethod = it)
        }
      )
    }

    item {
      Rows.ToggleRow(
        checked = terminalDonationState.value.error != null,
        text = "Enable error",
        onCheckChanged = {
          val error = if (it) {
            DonationErrorValue()
          } else {
            null
          }

          terminalDonationState.value = terminalDonationState.value.copy(error = error)
        }
      )
    }

    val error = terminalDonationState.value.error
    if (error != null) {
      item {
        DonationErrorValueTypeSelector(
          selectedPaymentMethodType = paymentMethodType,
          selectedErrorType = error.type,
          onErrorTypeSelected = {
            terminalDonationState.value = terminalDonationState.value.copy(
              error = error.copy(
                type = it,
                code = ""
              )
            )
          }
        )
      }

      item {
        DonationErrorValueCodeSelector(
          selectedPaymentMethodType = paymentMethodType,
          selectedErrorType = error.type,
          selectedErrorCode = error.code,
          onErrorCodeSelected = {
            terminalDonationState.value = terminalDonationState.value.copy(
              error = error.copy(
                code = it
              )
            )
          }
        )
      }
    }

    item {
      Buttons.LargeTonal(
        onClick = { onAddClick(terminalDonationState.value) },
        modifier = Modifier.defaultMinSize(minWidth = 220.dp)
      ) {
        Text(text = "Confirm")
      }
    }
  }
}
