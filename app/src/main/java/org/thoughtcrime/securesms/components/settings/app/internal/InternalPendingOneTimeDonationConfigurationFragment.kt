/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.Buttons
import org.signal.core.ui.Rows
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.theme.SignalTheme
import org.signal.donations.StripeDeclineCode
import org.signal.donations.StripeFailureCode
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.PayPalDeclineCode
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingOneTimeDonation
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Allows configuration of a PendingOneTimeDonation object to display different
 * states in the donation settings screen.
 */
class InternalPendingOneTimeDonationConfigurationFragment : ComposeFragment() {

  private val viewModel: InternalPendingOneTimeDonationConfigurationViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state
    Content(
      state,
      onNavigationClick = {
        findNavController().popBackStack()
      },
      onAddError = {
        viewModel.state.value = viewModel.state.value.copy(error = PendingOneTimeDonation.Error())
      },
      onClearError = {
        viewModel.state.value = viewModel.state.value.copy(error = null)
      },
      onPaymentMethodTypeSelected = {
        viewModel.state.value = viewModel.state.value.copy(paymentMethodType = it, error = null)
      },
      onErrorTypeSelected = {
        viewModel.state.value = viewModel.state.value.copy(error = viewModel.state.value.error!!.copy(type = it))
      },
      onErrorCodeChanged = {
        viewModel.state.value = viewModel.state.value.copy(error = viewModel.state.value.error!!.copy(code = it))
      },
      onSave = {
        SignalStore.donationsValues().setPendingOneTimeDonation(viewModel.state.value)
        findNavController().popBackStack()
      }
    )
  }
}

@Preview
@Composable
private fun ContentPreview() {
  SignalTheme {
    Surface {
      Content(
        state = PendingOneTimeDonation.Builder().error(PendingOneTimeDonation.Error()).build(),
        onNavigationClick = {},
        onClearError = {},
        onAddError = {},
        onPaymentMethodTypeSelected = {},
        onErrorTypeSelected = {},
        onErrorCodeChanged = {},
        onSave = {}
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
  state: PendingOneTimeDonation,
  onNavigationClick: () -> Unit,
  onAddError: () -> Unit,
  onClearError: () -> Unit,
  onPaymentMethodTypeSelected: (PendingOneTimeDonation.PaymentMethodType) -> Unit,
  onErrorTypeSelected: (PendingOneTimeDonation.Error.Type) -> Unit,
  onErrorCodeChanged: (String) -> Unit,
  onSave: () -> Unit
) {
  Scaffolds.Settings(
    title = "One-time donation state",
    navigationIconPainter = painterResource(id = R.drawable.symbol_arrow_left_24),
    navigationContentDescription = null,
    onNavigationClick = onNavigationClick
  ) {
    val isCodedError = remember(state.error?.type) {
      state.error?.type in setOf(PendingOneTimeDonation.Error.Type.PROCESSOR_CODE, PendingOneTimeDonation.Error.Type.DECLINE_CODE, PendingOneTimeDonation.Error.Type.FAILURE_CODE)
    }

    LazyColumn(
      horizontalAlignment = CenterHorizontally,
      modifier = Modifier.padding(it)
    ) {
      item {
        var expanded by remember {
          mutableStateOf(false)
        }

        ExposedDropdownMenuBox(
          expanded = expanded,
          onExpandedChange = {
            expanded = !expanded
          }
        ) {
          TextField(
            value = state.paymentMethodType.name,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
          )

          ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
          ) {
            PendingOneTimeDonation.PaymentMethodType.values().forEach { item ->
              DropdownMenuItem(
                text = { Text(text = item.name) },
                onClick = {
                  onPaymentMethodTypeSelected(item)
                  expanded = false
                }
              )
            }
          }
        }
      }

      item {
        Rows.ToggleRow(
          checked = state.error != null,
          text = "Enable error",
          onCheckChanged = {
            if (it) {
              onAddError()
            } else {
              onClearError()
            }
          }
        )
      }

      if (state.error != null) {
        item {
          var expanded by remember {
            mutableStateOf(false)
          }

          ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
              expanded = !expanded
            }
          ) {
            TextField(
              value = state.error.type.name,
              onValueChange = {},
              readOnly = true,
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
              modifier = Modifier.menuAnchor()
            )

            ExposedDropdownMenu(
              expanded = expanded,
              onDismissRequest = { expanded = false }
            ) {
              PendingOneTimeDonation.Error.Type.values().filterNot {
                state.paymentMethodType == PendingOneTimeDonation.PaymentMethodType.PAYPAL && it == PendingOneTimeDonation.Error.Type.FAILURE_CODE
              }.forEach { item ->
                DropdownMenuItem(
                  text = { Text(text = item.name) },
                  onClick = {
                    onErrorTypeSelected(item)
                    expanded = false
                  }
                )
              }
            }
          }
        }

        if (isCodedError) {
          item {
            var expanded by remember {
              mutableStateOf(false)
            }

            ExposedDropdownMenuBox(
              expanded = expanded,
              onExpandedChange = {
                expanded = !expanded
              }
            ) {
              TextField(
                value = state.error.code,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor()
              )

              ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
              ) {
                when (state.error.type) {
                  PendingOneTimeDonation.Error.Type.PROCESSOR_CODE -> {
                    ProcessorErrorsDropdown(state.paymentMethodType, onErrorCodeChanged)
                  }

                  PendingOneTimeDonation.Error.Type.DECLINE_CODE -> {
                    DeclineCodeErrorsDropdown(state.paymentMethodType, onErrorCodeChanged)
                  }

                  PendingOneTimeDonation.Error.Type.FAILURE_CODE -> {
                    FailureCodeErrorsDropdown(onErrorCodeChanged)
                  }

                  else -> error("This should never happen")
                }
              }
            }
          }
        }
      }

      item {
        Buttons.LargeTonal(
          enabled = state.badge != null,
          onClick = onSave
        ) {
          Text(text = "Save")
        }
      }
    }
  }
}

@Composable
private fun ColumnScope.ProcessorErrorsDropdown(
  paymentMethodType: PendingOneTimeDonation.PaymentMethodType,
  onErrorCodeSelected: (String) -> Unit
) {
  val values = when (paymentMethodType) {
    PendingOneTimeDonation.PaymentMethodType.PAYPAL -> arrayOf("2046", "2074")
    else -> arrayOf("currency_not_supported", "call_issuer")
  }

  ValuesDropdown(values = values, onErrorCodeSelected = onErrorCodeSelected)
}

@Composable
private fun ColumnScope.DeclineCodeErrorsDropdown(
  paymentMethodType: PendingOneTimeDonation.PaymentMethodType,
  onErrorCodeSelected: (String) -> Unit
) {
  val values = remember(paymentMethodType) {
    when (paymentMethodType) {
      PendingOneTimeDonation.PaymentMethodType.PAYPAL -> PayPalDeclineCode.KnownCode.values()
      else -> StripeDeclineCode.Code.values()
    }.map { it.name }.toTypedArray()
  }

  ValuesDropdown(values = values, onErrorCodeSelected = onErrorCodeSelected)
}

@Composable
private fun ColumnScope.FailureCodeErrorsDropdown(
  onErrorCodeSelected: (String) -> Unit
) {
  val values = remember {
    StripeFailureCode.Code.values().map { it.name }.toTypedArray()
  }

  ValuesDropdown(values = values, onErrorCodeSelected = onErrorCodeSelected)
}

@Composable
private fun ValuesDropdown(values: Array<String>, onErrorCodeSelected: (String) -> Unit) {
  values.forEach { item ->
    DropdownMenuItem(
      text = { Text(text = item) },
      onClick = {
        onErrorCodeSelected(item)
      }
    )
  }
}
