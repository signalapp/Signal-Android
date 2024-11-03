/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.donor

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.signal.donations.StripeDeclineCode
import org.signal.donations.StripeFailureCode
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.PayPalDeclineCode
import org.thoughtcrime.securesms.database.model.databaseprotos.DonationErrorValue
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingOneTimeDonation

/**
 * Displays a dropdown widget for selecting an error type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationErrorValueTypeSelector(
  selectedPaymentMethodType: PendingOneTimeDonation.PaymentMethodType,
  selectedErrorType: DonationErrorValue.Type,
  onErrorTypeSelected: (DonationErrorValue.Type) -> Unit
) {
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
      value = selectedErrorType.name,
      onValueChange = {},
      readOnly = true,
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier.menuAnchor()
    )

    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      DonationErrorValue.Type.entries.filterNot {
        selectedPaymentMethodType == PendingOneTimeDonation.PaymentMethodType.PAYPAL && it == DonationErrorValue.Type.FAILURE_CODE
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

/**
 * Displays a dropdown widget for selecting an error code, if the corresponding type
 * allows for such things.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationErrorValueCodeSelector(
  selectedPaymentMethodType: PendingOneTimeDonation.PaymentMethodType,
  selectedErrorType: DonationErrorValue.Type,
  selectedErrorCode: String,
  onErrorCodeSelected: (String) -> Unit
) {
  val isCodedError = remember(selectedErrorType) {
    selectedErrorType in setOf(DonationErrorValue.Type.PROCESSOR_CODE, DonationErrorValue.Type.DECLINE_CODE, DonationErrorValue.Type.FAILURE_CODE)
  }

  var expanded by remember {
    mutableStateOf(false)
  }

  if (isCodedError) {
    ExposedDropdownMenuBox(
      expanded = expanded,
      onExpandedChange = {
        expanded = !expanded
      }
    ) {
      TextField(
        value = selectedErrorCode,
        onValueChange = {},
        readOnly = true,
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        modifier = Modifier.menuAnchor()
      )

      ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
      ) {
        when (selectedErrorType) {
          DonationErrorValue.Type.PROCESSOR_CODE -> {
            ProcessorErrorsDropdown(selectedPaymentMethodType, onErrorCodeSelected)
          }

          DonationErrorValue.Type.DECLINE_CODE -> {
            DeclineCodeErrorsDropdown(selectedPaymentMethodType, onErrorCodeSelected)
          }

          DonationErrorValue.Type.FAILURE_CODE -> {
            FailureCodeErrorsDropdown(onErrorCodeSelected)
          }

          else -> error("This should never happen")
        }
      }
    }
  }
}

@Composable
private fun ProcessorErrorsDropdown(
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
private fun DeclineCodeErrorsDropdown(
  paymentMethodType: PendingOneTimeDonation.PaymentMethodType,
  onErrorCodeSelected: (String) -> Unit
) {
  val values = remember(paymentMethodType) {
    when (paymentMethodType) {
      PendingOneTimeDonation.PaymentMethodType.PAYPAL -> PayPalDeclineCode.KnownCode.entries.toTypedArray()
      else -> StripeDeclineCode.Code.entries.toTypedArray()
    }.map { it.name }.toTypedArray()
  }

  ValuesDropdown(values = values, onErrorCodeSelected = onErrorCodeSelected)
}

@Composable
private fun FailureCodeErrorsDropdown(
  onErrorCodeSelected: (String) -> Unit
) {
  val values = remember {
    StripeFailureCode.Code.entries.map { it.name }.toTypedArray()
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
