/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.ideal

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class IdealTransferDetailsViewModel : ViewModel() {

  private val internalState = mutableStateOf(IdealTransferDetailsState())
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

  fun onBankSelected(idealBank: IdealBank) {
    internalState.value = internalState.value.copy(
      idealBank = idealBank
    )
  }
}
