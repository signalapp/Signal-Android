package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [DonationReceiptDetailScreen].
 */
class DonationReceiptDetailViewModel(id: Long) : EventDrivenViewModel<DonationReceiptDetailEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(DonationReceiptDetailViewModel::class)
  }

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

  override suspend fun processEvent(event: DonationReceiptDetailEvent) {
    when (event) {
      DonationReceiptDetailEvent.NavigateBackClicked -> _actions.send(DonationReceiptDetailAction.NavigateBack)
      DonationReceiptDetailEvent.ShareClicked -> shareReceipt()
    }
  }

  /**
   * Renders outside the event queue so the guard sees [DonationReceiptDetailState.isRenderingReceipt] immediately;
   * awaiting it here would let a queued second tap start another render once the first finishes.
   */
  private fun shareReceipt() {
    val state = _state.value
    val record = state.inAppPaymentReceiptRecord
    if (record == null || state.isRenderingReceipt) {
      return
    }

    _state.update { it.copy(isRenderingReceipt = true) }
    viewModelScope.launch {
      val uri = DonationReceiptDetailRepository.renderReceiptImage(record)
      _state.update { it.copy(isRenderingReceipt = false) }
      _actions.send(DonationReceiptDetailAction.ShareReceipt(uri))
    }
  }
}
