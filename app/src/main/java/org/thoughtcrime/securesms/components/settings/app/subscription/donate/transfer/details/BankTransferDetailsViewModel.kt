/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.details

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.details.BankTransferDetailsState.FocusState

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

  fun onFocusChanged(field: Field, isFocused: Boolean) {
    when (field) {
      Field.IBAN -> {
        internalState.value = internalState.value.copy(
          ibanValidity = IBANValidator.validate(internalState.value.iban, isFocused)
        )
      }

      Field.NAME -> {
        if (isFocused && internalState.value.nameFocusState == FocusState.NOT_FOCUSED) {
          internalState.value = internalState.value.copy(nameFocusState = FocusState.FOCUSED)
        } else if (!isFocused && internalState.value.nameFocusState == FocusState.FOCUSED) {
          internalState.value = internalState.value.copy(nameFocusState = FocusState.LOST_FOCUS)
        }
      }

      Field.EMAIL -> {
        if (isFocused && internalState.value.emailFocusState == FocusState.NOT_FOCUSED) {
          internalState.value = internalState.value.copy(emailFocusState = FocusState.FOCUSED)
        } else if (!isFocused && internalState.value.emailFocusState == FocusState.FOCUSED) {
          internalState.value = internalState.value.copy(emailFocusState = FocusState.LOST_FOCUS)
        }
      }
    }
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

  enum class Field {
    IBAN,
    NAME,
    EMAIL
  }
}
