/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.details

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class BankTransferDetailsViewModel : ViewModel() {

  companion object {
    private const val IBAN_MAX_CHARACTER_COUNT = 34
  }

  private val internalState = mutableStateOf(BankTransferDetailsState())
  val state: State<BankTransferDetailsState> = internalState

  fun setDisplayFindAccountInfoSheet(displayFindAccountInfoSheet: Boolean) {
    internalState.value = internalState.value.copy(
      displayFindAccountInfoSheet = displayFindAccountInfoSheet
    )
  }

  fun onNameChanged(name: String) {
    internalState.value = internalState.value.copy(
      name = name
    )
  }

  fun onIBANFocusChanged(isFocused: Boolean) {
    internalState.value = internalState.value.copy(
      ibanValidity = IBANValidator.validate(internalState.value.iban, isFocused)
    )
  }

  fun onIBANChanged(iban: String) {
    internalState.value = internalState.value.copy(
      iban = iban.take(IBAN_MAX_CHARACTER_COUNT).uppercase(),
      ibanValidity = IBANValidator.validate(internalState.value.iban, true)
    )
  }

  fun onEmailChanged(email: String) {
    internalState.value = internalState.value.copy(
      email = email
    )
  }
}
