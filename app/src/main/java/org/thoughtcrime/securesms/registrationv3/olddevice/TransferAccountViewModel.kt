/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.olddevice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.registrationv3.data.QuickRegistrationRepository

class TransferAccountViewModel(reRegisterUri: String) : ViewModel() {

  private val store: MutableStateFlow<TransferAccountState> = MutableStateFlow(TransferAccountState(reRegisterUri))

  val state: StateFlow<TransferAccountState> = store

  fun transferAccount() {
    viewModelScope.launch(Dispatchers.IO) {
      store.update { it.copy(inProgress = true) }
      val result = QuickRegistrationRepository.transferAccount(store.value.reRegisterUri)
      store.update { it.copy(reRegisterResult = result, inProgress = false) }
    }
  }

  fun clearReRegisterResult() {
    store.update { it.copy(reRegisterResult = null) }
  }

  data class TransferAccountState(
    val reRegisterUri: String,
    val inProgress: Boolean = false,
    val reRegisterResult: QuickRegistrationRepository.TransferAccountResult? = null
  )
}
