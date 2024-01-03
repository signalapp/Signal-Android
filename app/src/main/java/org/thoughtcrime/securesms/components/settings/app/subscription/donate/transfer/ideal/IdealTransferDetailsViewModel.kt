/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.ideal

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class IdealTransferDetailsViewModel(isMonthly: Boolean) : ViewModel() {

  private val internalState = mutableStateOf(IdealTransferDetailsState(isMonthly = isMonthly))
  var state: State<IdealTransferDetailsState> = internalState

  fun onNameChanged(name: String) {
    internalState.value = internalState.value.copy(
      name = name
    )
  }

  fun onEmailChanged(email: String) {
    internalState.value = internalState.value.copy(
      email = email
    )
  }

  fun onFocusChanged(field: Field, isFocused: Boolean) {
    when (field) {
      Field.NAME -> {
        if (isFocused && internalState.value.nameFocusState == IdealTransferDetailsState.FocusState.NOT_FOCUSED) {
          internalState.value = internalState.value.copy(nameFocusState = IdealTransferDetailsState.FocusState.FOCUSED)
        } else if (!isFocused && internalState.value.nameFocusState == IdealTransferDetailsState.FocusState.FOCUSED) {
          internalState.value = internalState.value.copy(nameFocusState = IdealTransferDetailsState.FocusState.LOST_FOCUS)
        }
      }

      Field.EMAIL -> {
        if (isFocused && internalState.value.emailFocusState == IdealTransferDetailsState.FocusState.NOT_FOCUSED) {
          internalState.value = internalState.value.copy(emailFocusState = IdealTransferDetailsState.FocusState.FOCUSED)
        } else if (!isFocused && internalState.value.emailFocusState == IdealTransferDetailsState.FocusState.FOCUSED) {
          internalState.value = internalState.value.copy(emailFocusState = IdealTransferDetailsState.FocusState.LOST_FOCUS)
        }
      }
    }
  }

  fun onBankSelected(idealBank: IdealBank) {
    internalState.value = internalState.value.copy(
      idealBank = idealBank
    )
  }

  enum class Field {
    NAME,
    EMAIL
  }
}
