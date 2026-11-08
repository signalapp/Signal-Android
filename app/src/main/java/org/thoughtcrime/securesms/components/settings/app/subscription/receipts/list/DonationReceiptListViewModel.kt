/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [DonationReceiptListScreen]. Fetches the badges on start, then again each time the device comes back online
 * until a fetch succeeds.
 */
class DonationReceiptListViewModel : EventDrivenViewModel<DonationReceiptListEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(DonationReceiptListViewModel::class)
  }

  private val _state = MutableStateFlow(DonationReceiptListState())
  private val _actions = Channel<DonationReceiptListAction>(Channel.BUFFERED)

  val state: StateFlow<DonationReceiptListState> = _state.asStateFlow()
  val actions: Flow<DonationReceiptListAction> = _actions.receiveAsFlow()

  init {
    viewModelScope.launch {
      val receipts = DonationReceiptListRepository.getReceipts()
      _state.update { it.copy(receipts = receipts) }
    }

    viewModelScope.launch {
      DonationReceiptListRepository.observeIsConnected()
        .onStart { emit(true) }
        .filter { isConnected -> isConnected && _state.value.badges.isEmpty() }
        .collect {
          val badges = DonationReceiptListRepository.getBadges()
          _state.update { it.copy(badges = badges) }
        }
    }
  }

  override suspend fun processEvent(event: DonationReceiptListEvent) {
    when (event) {
      DonationReceiptListEvent.NavigateBackClicked -> _actions.send(DonationReceiptListAction.NavigateBack)
      is DonationReceiptListEvent.ReceiptClicked -> _actions.send(DonationReceiptListAction.NavigateToReceipt(event.receiptId))
    }
  }
}
