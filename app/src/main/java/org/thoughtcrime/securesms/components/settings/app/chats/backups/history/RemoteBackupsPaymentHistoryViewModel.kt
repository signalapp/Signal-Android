/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemoteBackupsPaymentHistoryViewModel : ViewModel() {

  private val internalStateFlow = MutableStateFlow(RemoteBackupsPaymentHistoryState())
  val state: StateFlow<RemoteBackupsPaymentHistoryState> = internalStateFlow

  init {
    viewModelScope.launch {
      val receipts = withContext(Dispatchers.IO) {
        RemoteBackupsPaymentHistoryRepository.getReceipts()
      }

      internalStateFlow.update { state -> state.copy(records = receipts.associateBy { it.id }.toPersistentMap()) }
    }
  }

  fun onStartRenderingBitmap() {
    internalStateFlow.update { it.copy(displayProgressDialog = true) }
  }

  fun onEndRenderingBitmap() {
    internalStateFlow.update { it.copy(displayProgressDialog = false) }
  }
}
