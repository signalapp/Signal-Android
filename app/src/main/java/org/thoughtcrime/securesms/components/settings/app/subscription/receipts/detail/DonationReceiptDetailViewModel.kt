package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.components.settings.app.subscription.receipts.ReceiptImageRenderer

class DonationReceiptDetailViewModel(id: Long) : ViewModel() {
  private val _state = MutableStateFlow(DonationReceiptDetailState())
  private val _actions = Channel<DonationReceiptDetailAction>(Channel.BUFFERED)

  val state: StateFlow<DonationReceiptDetailState> = _state.asStateFlow()
  val actions: Flow<DonationReceiptDetailAction> = _actions.receiveAsFlow()

  init {
    viewModelScope.launch {
      val record = DonationReceiptDetailRepository.getDonationReceiptRecord(id)
      _state.update { it.copy(inAppPaymentReceiptRecord = record) }
    }
  }

  fun shareReceipt(context: Context) {
    val record = _state.value.inAppPaymentReceiptRecord
    if (record == null || _state.value.isRenderingReceipt) {
      return
    }

    _state.update { it.copy(isRenderingReceipt = true) }
    viewModelScope.launch {
      val uri = ReceiptImageRenderer.renderPng(context, record)
      _state.update { it.copy(isRenderingReceipt = false) }
      _actions.send(DonationReceiptDetailAction.ShareReceipt(uri))
    }
  }
}
